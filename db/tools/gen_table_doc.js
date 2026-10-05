// 從 V1 DDL 產出給 DBA 審的 Table List／Table Schema xlsx（純 node，無外部套件）
// 用法（在 repo 根目錄）：node db/tools/gen_table_doc.js [ddl路徑] [輸出xlsx路徑]
// 預設：db/oracle/V1__init_schema.sql → docs/db/Table_List_Schema.xlsx
const fs = require('fs');
const zlib = require('zlib');

const ddlPath = process.argv[2] || 'db/oracle/V1__init_schema.sql';
const xlsxPath = process.argv[3] || 'docs/db/Table_List_Schema.xlsx';
const src = fs.readFileSync(ddlPath, 'utf8').replace(/\r\n/g, '\n');
const SYSTEM = 'IM（機房設備異動）';

const MASTERS = new Set(['IM_USER', 'IM_ROLE', 'IM_FLOW', 'IM_FLOW_STEP', 'IM_FORM_OPTION', 'IM_TMPL', 'IM_RACK_CACHE']);
const RETENTION = {
  IM_ACCESS_LOG: '待定',
  IM_MAIL_OUTBOX: '待定',
  IM_LOGIN_TOKEN: '待定',
  IM_RACK_CACHE: '待定',
};

const unq = (s) => s.replace(/''/g, "'");
const tableComment = {};
const colComment = {};
for (const m of src.matchAll(/^COMMENT ON TABLE\s+(\w+)\s+IS\s+'((?:[^']|'')*)';/gm)) tableComment[m[1]] = unq(m[2]);
for (const m of src.matchAll(/^COMMENT ON COLUMN\s+(\w+)\.(\w+)\s+IS\s+'((?:[^']|'')*)';/gm)) colComment[`${m[1]}.${m[2]}`] = unq(m[3]);

const tables = [];
for (const m of src.matchAll(/^CREATE TABLE (\w+) \(\n([\s\S]*?)\n\);/gm)) {
  const cols = [];
  let pk = [];
  for (const line of m[2].split('\n')) {
    const pm = line.match(/CONSTRAINT\s+PK_\w+\s+PRIMARY KEY\s*\(([^)]*)\)/);
    if (pm) { pk = pm[1].split(',').map((s) => s.trim()); continue; }
    const cm = line.match(/^\s{4}([A-Z][A-Z0-9_]*)\s+(NUMBER\(\d+(?:,\d+)?\)|VARCHAR2\([^)]*\)|DATE|CLOB)(.*)$/);
    if (!cm) continue;
    const notNull = /NOT NULL/.test(cm[3]) || /AS IDENTITY/.test(cm[3]);
    cols.push({ name: cm[1], type: cm[2], notNull });
  }
  tables.push({ name: m[1], cols, pk });
}

const problems = [];
if (tables.length !== 31) problems.push(`表數 ${tables.length} ≠ 31`);
for (const t of tables) {
  if (!tableComment[t.name]) problems.push(`缺表說明 ${t.name}`);
  if (!t.pk.length) problems.push(`缺 PK ${t.name}`);
  for (const c of t.cols) if (!colComment[`${t.name}.${c.name}`]) problems.push(`缺欄說明 ${t.name}.${c.name}`);
}
const parsedCols = tables.reduce((n, t) => n + t.cols.length, 0);
if (Object.keys(colComment).length !== parsedCols) problems.push(`欄位數 ${parsedCols} ≠ 欄說明數 ${Object.keys(colComment).length}`);
if (problems.length) { console.error(problems.join('\n')); process.exit(1); }

const typeOf = (n) => (n === 'SYS_PARAM' ? '系統參數表' : MASTERS.has(n) ? '主檔類' : /_MAP$/.test(n) ? '對照表' : '機房異動業務');
const listRows = [['系統', '#', '類型', 'TABLE 名稱', '說明', '備註', '資料保留天數']];
tables.forEach((t, i) => {
  const full = tableComment[t.name];
  const idx = full.indexOf('。');
  listRows.push([SYSTEM, i + 1, typeOf(t.name), t.name, idx < 0 ? full : full.slice(0, idx), idx < 0 ? '' : full.slice(idx + 1), RETENTION[t.name] || '永久']);
});
const schemaRows = [
  ['', '', '', '', '', '', '這一欄建表時,會寫到DB字典(簡單明確原則).', '', ''],
  ['系統', 'Table名稱', '欄位名稱', '資料型別', 'Nullable', 'PK', '備註/說明', 'Sample Data', '資料來源'],
];
for (const t of tables) {
  for (const c of t.cols) {
    schemaRows.push([SYSTEM, t.name, c.name, c.type, c.notNull ? 'NOT NULL' : '', t.pk.includes(c.name) ? 'Y' : '', colComment[`${t.name}.${c.name}`], '', '']);
  }
}
const qRows = [
  ['#', '問題', '本檔做法', '請 DBA 回覆'],
  [1, 'SYS_PARAM 的 NAME／VALUE／DESCR／MEMO 是單字欄名，與規範 11（避免單字欄名，須加實體前綴）衝突；範例工作表卻是這四個欄名', '沿用範例欄名', '以範例或條文為準？'],
  [2, '範例 SYS_PARAM 欄名用 DESCR，縮寫字典是 DESC（DESCRIPTION）；範例的 PARAM 與字典 PARM 亦不一致', '沿用範例寫法（DESCR）', '以範例或字典為準？'],
  [3, '應用帳號 ap_user 對 31 張表都授權 SELECT／INSERT／UPDATE／DELETE；其中 IM_ACCESS_LOG（存取紀錄）、IM_APP_EVENT（狀態事件）、IM_APP_VER（版次快照）性質上只新增不修改，是否收緊為只給 SELECT／INSERT', '四種權限都給', '依公司慣例是否收緊？'],
];

// ---- 最小 xlsx 寫入器（inline string）----
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/[\x00-\x08\x0b\x0c\x0e-\x1f]/g, '');
const colLetter = (i) => String.fromCharCode(65 + i);
const sheetXml = (rows, widths) => {
  const isSchema = widths.length === 9;
  const cols = widths.map((w, i) => `<col min="${i + 1}" max="${i + 1}" width="${w}" customWidth="1"/>`).join('');
  const body = rows.map((r, ri) => {
    const cells = r.map((v, ci) => {
      const ref = `${colLetter(ci)}${ri + 1}`;
      const s = ri === 0 || (isSchema && ri === 1) ? ' s="1"' : ' s="2"';
      if (typeof v === 'number') return `<c r="${ref}"${s}><v>${v}</v></c>`;
      if (v === '') return `<c r="${ref}"${s}/>`;
      return `<c r="${ref}"${s} t="inlineStr"><is><t xml:space="preserve">${esc(v)}</t></is></c>`;
    }).join('');
    return `<row r="${ri + 1}">${cells}</row>`;
  }).join('');
  const frozen = isSchema ? 2 : 1;
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="${frozen}" topLeftCell="A${frozen + 1}" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols>${cols}</cols><sheetData>${body}</sheetData></worksheet>`;
};
const sheetOverride = (n) => `<Override PartName="/xl/worksheets/sheet${n}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`;
const sheetRel = (n) => `<Relationship Id="rId${n}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${n}.xml"/>`;
const files = {
  '[Content_Types].xml': `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheetOverride(1)}${sheetOverride(2)}${sheetOverride(3)}</Types>`,
  '_rels/.rels': `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`,
  'xl/workbook.xml': `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Table List" sheetId="1" r:id="rId1"/><sheet name="Table Schema" sheetId="2" r:id="rId2"/><sheet name="待DBA回覆" sheetId="3" r:id="rId3"/></sheets></workbook>`,
  'xl/_rels/workbook.xml.rels': `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheetRel(1)}${sheetRel(2)}${sheetRel(3)}<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>`,
  'xl/styles.xml': `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Microsoft JhengHei"/></font><font><b/><sz val="11"/><name val="Microsoft JhengHei"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFD9E1F2"/></patternFill></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="3"><xf/><xf fontId="1" fillId="2" applyFont="1" applyFill="1"><alignment vertical="top" wrapText="1"/></xf><xf applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf></cellXfs></styleSheet>`,
  'xl/worksheets/sheet1.xml': sheetXml(listRows, [18, 5, 14, 26, 40, 80, 24]),
  'xl/worksheets/sheet2.xml': sheetXml(schemaRows, [18, 26, 26, 22, 12, 6, 80, 16, 16]),
  'xl/worksheets/sheet3.xml': sheetXml(qRows, [5, 80, 28, 24]),
};

// ---- 最小 zip 寫入器 ----
const chunks = [];
const central = [];
let offset = 0;
for (const [name, text] of Object.entries(files)) {
  const nameBuf = Buffer.from(name, 'utf8');
  const raw = Buffer.from(text, 'utf8');
  const comp = zlib.deflateRawSync(raw);
  const crc = zlib.crc32(raw);
  const lh = Buffer.alloc(30);
  lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt16LE(0x0800, 6); lh.writeUInt16LE(8, 8);
  lh.writeUInt16LE(0, 10); lh.writeUInt16LE(0x21, 12);
  lh.writeUInt32LE(crc, 14); lh.writeUInt32LE(comp.length, 18); lh.writeUInt32LE(raw.length, 22); lh.writeUInt16LE(nameBuf.length, 26);
  chunks.push(lh, nameBuf, comp);
  const ch = Buffer.alloc(46);
  ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6); ch.writeUInt16LE(0x0800, 8); ch.writeUInt16LE(8, 10);
  ch.writeUInt16LE(0, 12); ch.writeUInt16LE(0x21, 14);
  ch.writeUInt32LE(crc, 16); ch.writeUInt32LE(comp.length, 20); ch.writeUInt32LE(raw.length, 24); ch.writeUInt16LE(nameBuf.length, 28);
  ch.writeUInt32LE(offset, 42);
  central.push(ch, nameBuf);
  offset += lh.length + nameBuf.length + comp.length;
}
const cdBuf = Buffer.concat(central);
const end = Buffer.alloc(22);
const n = Object.keys(files).length;
end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(n, 8); end.writeUInt16LE(n, 10);
end.writeUInt32LE(cdBuf.length, 12); end.writeUInt32LE(offset, 16);
fs.writeFileSync(xlsxPath, Buffer.concat([...chunks, cdBuf, end]));

console.log(`表 ${tables.length}、欄位 ${parsedCols}`);
console.log('類型分布', listRows.slice(1).reduce((o, r) => (o[r[2]] = (o[r[2]] || 0) + 1, o), {}));
