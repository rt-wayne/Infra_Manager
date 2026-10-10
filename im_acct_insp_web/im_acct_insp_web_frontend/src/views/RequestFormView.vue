<!--
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 新增：新增／異動申請表單（開發文件 spec_im_acct_insp.md 第 6 節；版面、提示文字依原型 view-acc-form）
            query：無＝新增；reqType＋acctId＝對既有帳號異動（GET /accounts/{id} 帶入，類別／名稱／系統鎖定）；
            resubmit＝退件後重新送出（GET /requests/{id} 帶入，申請類型鎖定，非新增單另鎖識別欄位）
            六節：基本資料、帳號資訊（8 類別、門禁卡欄位切換與提示、資產分類碼橘色標記與每半年提示、特權）、
            使用人（同申請人）、權限內容、有效期限、簽核路徑預覽（/codes 的 stages＋/users 人員）；本人確認勾選
            送出前驗證順序同 4.3（utils/requestForm.ts）；後端 400／409 的 message 以 toast 顯示
            附件第一階段只記檔名（attachFileName），不上傳檔案
            變更單 #1：S1 異動申請帶入改用 GET /accounts/lookup/{id}（不再呼叫 /accounts/{id}，無帳號列表權限者也可帶入）；
            M3 重新送出時附件檔名留空
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: v2.0（開發文件 spec_im_acct_insp_v2.0.md 第 4.2、4.3、4.9、4.10、8.3 節）：
            刪展延；新增時類別下拉只列 enabled；suggestExpiry 類別在期限旁顯示 U04；申請日期唯讀（重送顯示原申請日期）；
            新單申請單位空白、重送保留；申請人分機與 Email 灰底、只當「同申請人」帶入來源、不送出；
            「同申請人」：使用人＝登入者顯示名、聯絡方式＝分機→Email→使用者資料、門禁卡 Email＝表單 Email→使用者資料、所屬單位空白才帶申請單位；
            停用／刪除只可填原因：隱藏權限範圍、存取對象、有效期限，原因欄名改「停用原因」「刪除原因」，刪除顯示 U03；
            異動帶入 GET /accounts/lookup/{id}?acctName；資產分類只列可選碼、審查條件碼加標記；
            公文附件（選填，1 個）：選檔先檢查副檔名與 10 MB，送出前讀檔，失敗出 U07 確認後不附公文送出；
            送出改 multipart（送出 POST /requests、重送 POST /requests/{id}/resubmit 帶 versionNo）；
            驗證 E01～E04（含到期日不可早於今天）；簽核路徑預覽改列 7 關名稱
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 變更單 #1（M1，spec v2.0 第 4.9 節）：門禁卡新增申請（含退件重送）不渲染帳號名稱欄（v-if）；
            修改／停用／重新啟用／刪除門禁卡時欄名改「帳號名稱（卡號）」，以唯讀（disabled＋readonly）顯示 lookup/{id} 帶入的卡號
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-07
  變更說明: 變更單 #2：門禁卡「卡號無法在申請時預先填寫」提示只在新增申請時顯示（v-if="doorAdd"）
  AI版本  : Claude Opus 5.5 (claude-opus-5-5)
  修改日期: 2026-10-10
  變更說明: 新增申請退件重送也鎖定帳號類別（catgLocked；原只鎖異動單），提示改「不可變更申請類型與帳號類別」
-->
<template>
  <main>
    <div v-if="banner" class="section-note">🎯 <span>{{ banner }}</span></div>

    <section class="form-section">
      <h3>{{ secTitle('basic') }}</h3>
      <div class="grid2">
        <div class="field"><label for="reqNo">申請單號</label><input id="reqNo" type="text" :value="resubmitId || '（送出後產生）'" disabled /></div>
        <div class="field"><label for="applyDate">申請日期</label><input id="applyDate" type="text" :value="applyDateText" disabled /></div>
        <div class="field"><label for="applicant">申請人</label><input id="applicant" type="text" :value="userName" disabled /></div>
        <div class="field"><label for="applyDept">申請單位</label><input id="applyDept" v-model="form.applyDept" type="text" placeholder="請填寫申請單位" /></div>
        <div class="field">
          <label for="applyTelNo">申請人分機</label>
          <input id="applyTelNo" v-model="form.applyTelNo" class="sample" type="text" placeholder="例：1234" />
        </div>
        <div class="field">
          <label for="applyEmail">申請人 Email</label>
          <input id="applyEmail" v-model="form.applyEmail" class="sample" type="text" placeholder="例：name@pxmart.com.tw" />
        </div>
      </div>
      <div class="hint apply-contact-hint">ⓘ 申請人分機與 Email 僅供下方「同申請人」帶入使用，不會隨申請單送出或儲存。</div>
    </section>

    <section class="form-section">
      <h3>{{ secTitle('acct') }}</h3>
      <div class="field">
        <label>申請類型</label>
        <div class="radio-row">
          <label v-for="t in REQ_TYPE_ORDER" :key="t" :class="{ off: t !== form.reqTypeCode }">
            <input type="radio" name="reqType" :value="t" :checked="t === form.reqTypeCode" :disabled="t !== form.reqTypeCode" /> {{ REQ_TYPE_LABEL[t] }}
          </label>
        </div>
        <div class="hint">{{ typeHint }}</div>
      </div>

      <div class="grid2">
        <div class="field">
          <label for="catgId">帳號類別</label>
          <select id="catgId" v-model="form.catgId" :disabled="catgLocked">
            <option v-for="c in catgOptions" :key="c.catgId" :value="c.catgId">{{ c.catgName }}</option>
          </select>
          <div v-if="catgDescr" class="hint">{{ catgDescr }}</div>
        </div>
        <div v-if="!doorAdd" class="field">
          <label for="acctName">{{ isDoor ? '帳號名稱（卡號）' : '帳號名稱' }}</label>
          <input
            id="acctName"
            v-model="form.acctName"
            type="text"
            :readonly="isDoor"
            :disabled="identityLocked || isDoor || contentLocked"
            placeholder="請填寫帳號名稱"
          />
        </div>
      </div>

      <div v-if="isDoor" class="field door-fields">
        <div class="grid2">
          <div class="field">
            <label for="doorScope">門禁權限範圍</label>
            <select id="doorScope" v-model="form.doorScopeCode" :disabled="contentLocked">
              <option v-for="o in DOOR_SCOPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </div>
          <div class="field">
            <label for="holderEmail">使用人 Email</label>
            <input id="holderEmail" v-model="form.holderEmail" type="text" placeholder="請填寫使用人 Email" :disabled="contentLocked" />
          </div>
        </div>
        <div class="hint">ⓘ 機房為兩道門的縱深管制：同一張卡先刷過第一道門（測試區），再刷過第二道門才是正式機房。這是同一張卡、同一次申請，差別只在這張卡被授權可以刷開幾道門。</div>
        <div v-if="doorAdd" class="hint">ⓘ 卡號無法在申請時預先填寫——須待執行人實際刷過卡鐘、讀出卡號後才會確定，因此「帳號名稱」此類別不需填寫，將於執行完畢回報時登記。</div>
        <div class="hint">ⓘ 機房只有一套門禁管制系統（機房門禁管制系統），不需另外填寫所屬系統/設備名稱。</div>
      </div>
      <div v-else class="field">
        <label for="sysName">所屬系統/設備名稱</label>
        <input id="sysName" v-model="form.sysName" type="text" placeholder="請填寫所屬系統或設備名稱" :disabled="identityLocked || contentLocked" />
      </div>

      <div class="field">
        <label for="assetClassId">資產分類碼（依 SR-008-A04 資訊資產標示、保護與處置說明表）
          <button type="button" class="btn-link inline" @click="showAssetRef = true">📖 查看分類說明</button>
        </label>
        <select id="assetClassId" v-model="form.assetClassId" :disabled="isDoor || contentLocked">
          <option value="">未分類</option>
          <optgroup v-for="g in assetGroups" :key="g.label" :label="g.label">
            <option v-for="a in g.items" :key="a.assetClassId" :value="a.assetClassId" :class="{ direct: a.reviewTrigger }">
              {{ a.assetClassId }}　{{ a.assetClassName }}{{ a.reviewTrigger ? ' ⚠️' : '' }}{{ a.selectable ? '' : '（不可選用）' }}
            </option>
          </optgroup>
        </select>
        <div class="hint">⚠️ 標記（{{ triggerIds }}）為審查條件碼，帳號生效後列入定期權限審查。</div>
        <div v-if="assetIsTrigger" class="hint warn asset-review-hint">⚠️ 此分類碼為審查條件碼，帳號生效後將寫入審查日期，列入定期權限審查。</div>
      </div>

      <label class="check-box priv">
        <input id="isPriv" v-model="form.isPriv" type="checkbox" :disabled="contentLocked" />
        <span>此為特權帳號（root / Administrator / sa / enable 模式等）</span>
      </label>
      <div v-if="form.isPriv" class="hint warn priv-hint">⚠️ 特權帳號不論資產分類碼為何，帳號生效後一律列入定期權限審查。</div>
    </section>

    <section class="form-section">
      <h3>{{ secTitle('holder') }}</h3>
      <div class="hint top">服務帳號、廠商帳號的「申請人」與「實際使用人」通常不同，此處需獨立記錄以利稽核追蹤。</div>
      <div class="grid2">
        <div class="field">
          <label for="holderName">實際使用人 / 保管人
            <button v-if="!contentLocked" type="button" class="btn-link inline same-as" @click="copyApplicant">同申請人</button>
          </label>
          <input id="holderName" v-model="form.holderName" type="text" placeholder="請填寫實際使用人或保管人" :disabled="contentLocked" />
        </div>
        <div class="field"><label for="holderDept">所屬單位</label><input id="holderDept" v-model="form.holderDept" type="text" placeholder="請填寫所屬單位" :disabled="contentLocked" /></div>
      </div>
      <div v-if="!isDoor" class="field">
        <label for="holderCntct">使用人聯絡方式</label>
        <input id="holderCntct" v-model="form.holderCntct" type="text" placeholder="請填寫使用人的分機、電話或 Email（外部廠商請務必填寫）" :disabled="contentLocked" />
      </div>
      <div v-else class="hint">ⓘ 機房門禁卡已在上方填寫「使用人 Email」，不需再填聯絡方式。</div>
    </section>

    <section class="form-section">
      <h3>{{ secTitle('scope') }}</h3>
      <div v-if="contentLocked" class="hint top scope-na">{{ form.reqTypeCode === 'DELETE'
        ? 'ⓘ 「刪除」不需要填寫權限範圍、存取對象與有效期限，僅需說明刪除原因。'
        : 'ⓘ 「停用」為可逆動作，之後可申請「重新啟用」恢復；不需要填寫權限範圍、存取對象與有效期限。' }}</div>
      <div v-if="form.reqTypeCode === 'DELETE'" class="note-box warn delete-warn">⚠️ {{ MSG_DELETE_WARN }}</div>
      <template v-if="!contentLocked">
        <div v-if="!isDoor" class="grid2 scope-fields">
          <div class="field">
            <label for="accessLevel">權限範圍 / 角色</label>
            <select id="accessLevel" v-model="form.accessLevelCode">
              <option v-for="o in ACCESS_LEVEL_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </div>
          <div class="field"><label for="accessTarget">存取對象</label><input id="accessTarget" v-model="form.accessTarget" type="text" placeholder="請填寫存取對象" /></div>
        </div>
        <div v-else class="hint top">ⓘ 門禁卡沒有「唯讀／操作／管理員」這種分級，通行範圍已由上方「門禁權限範圍」決定，不需另外填寫。</div>
      </template>
      <div class="field">
        <label for="applyReason">{{ reasonText.label }}</label>
        <textarea id="applyReason" v-model="form.applyReason" rows="2" :placeholder="reasonText.placeholder"></textarea>
      </div>
    </section>

    <section class="form-section">
      <h3>{{ secTitle('attach') }}</h3>
      <div class="field">
        <label for="docFile">公文附件（選填，1 個；限 .pdf .doc .docx .jpg .png，10 MB 以內）</label>
        <input id="docFile" ref="docInput" type="file" :accept="ATTACH_ACCEPT" @change="onDocFile" />
        <div v-if="docFile" class="hint doc-file-name">
          已選擇：{{ docFile.name }}（{{ fileSizeText(docFile.size) }}）
          <button type="button" class="btn-link inline doc-clear" @click="clearDoc">移除</button>
        </div>
        <div v-for="a in oldDocs" :key="a.attachId" class="hint old-doc">目前公文：{{ a.fileName }}{{ docFile ? '（送出後由新選的檔案取代）' : '' }}</div>
      </div>
    </section>

    <section v-if="!contentLocked" class="form-section">
      <h3>{{ secTitle('expiry') }}</h3>
      <div class="grid2">
        <div class="field">
          <label>有效期限類型</label>
          <div class="radio-row">
            <label><input v-model="form.expiryTypeCode" type="radio" name="expiry" value="PERM" /> 永久有效</label>
            <label><input v-model="form.expiryTypeCode" type="radio" name="expiry" value="DATE" /> 指定到期日</label>
          </div>
          <div v-if="suggestExpiry" class="hint warn suggest-expiry">ⓘ {{ MSG_SUGGEST_EXPIRY }}</div>
        </div>
        <div class="field"><label for="expiryDate">到期日</label><input id="expiryDate" v-model="form.expiryDate" type="date" :min="today" :disabled="form.expiryTypeCode !== 'DATE'" /></div>
      </div>
    </section>

    <section class="form-section">
      <h3>{{ secTitle('preview') }}</h3>
      <div class="approval-preview" :class="{ priv: form.isPriv }">
        <div v-if="form.isPriv" class="priv-banner">⚠️ 此為特權帳號，以下每一關簽核請特別審慎確認權限範圍與必要性</div>
        <template v-for="(s, i) in preview" :key="i">
          <div v-if="i > 0" class="approval-arrow">↓</div>
          <div class="approval-step">
            <span class="step-num">{{ i + 1 }}</span>
            <span class="step-role">{{ s }}</span>
          </div>
        </template>
      </div>
    </section>

    <label class="check-box confirm">
      <input id="isUniqueConf" v-model="form.isUniqueConf" type="checkbox" />
      <span>本人確認：此帳號為唯一識別，僅供本人／指定使用人使用，不會與他人共用或轉借（依存取控制管理程序書 5.3.4 條規定）。</span>
    </label>

    <div class="form-actions">
      <button type="button" class="btn" @click="cancel">取消</button>
      <button type="button" class="btn btn-primary submit" :disabled="submitting || !ready" @click="submit">
        {{ submitting ? '送出中…' : resubmitId ? '重新送出' : '送出申請' }}
      </button>
    </div>

    <BaseModal
      v-if="showAssetRef"
      title="📖 資訊資產分類說明"
      sub="依 SR-008-A04《資訊資產標示、保護與處置說明表》；標記 ⭐ 者為審查條件碼。"
      wide
      @close="showAssetRef = false"
    >
      <AssetClassTable :rows="codes?.assetClasses ?? []" />
    </BaseModal>

    <BaseModal v-if="docReadFailed" title="公文附件讀取失敗" close-text="取消" @close="docReadFailed = false">
      <p class="doc-read-fail">{{ MSG_DOC_READ_FAIL }}</p>
      <template #actions>
        <button type="button" class="btn btn-primary send-without-doc" :disabled="submitting" @click="sendWithoutDoc">不附公文直接送出</button>
      </template>
    </BaseModal>
    <ToastHost />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AssetClassTable from '../components/AssetClassTable.vue'
import BaseModal from '../components/BaseModal.vue'
import ToastHost from '../components/ToastHost.vue'
import { getLookupAccount } from '../api/account'
import { errorMessage, errorStatus } from '../api/errors'
import { getRequest, resubmitRequest, submitRequest } from '../api/request'
import { useSession } from '../composables/useSession'
import { useToast } from '../composables/useToast'
import type { ReqTypeCode } from '../types/account'
import type { AssetClass, Category } from '../types/common'
import type { Attachment } from '../types/request'
import { ATTACH_ACCEPT, MSG_DOC_READ_FAIL, checkAttachFile, fileSizeText, readAttachFile } from '../utils/attach'
import { buildPreview, isReviewTriggerAsset, suggestExpiryOf } from '../utils/codes'
import { dateOnly, fmtDate } from '../utils/date'
import {
  ACCESS_LEVEL_OPTIONS,
  DOOR_CATG_ID,
  DOOR_SCOPE_OPTIONS,
  REQ_TYPE_LABEL,
  REQ_TYPE_ORDER,
  isReqType
} from '../utils/labels'
import {
  MSG_DELETE_WARN,
  emptyForm,
  formFromAccount,
  formFromRequest,
  isReasonOnly,
  reasonFieldName,
  toPayload,
  validateRequestForm
} from '../utils/requestForm'

/** U04 */
const MSG_SUGGEST_EXPIRY = '建議指定明確到期日'
const SEC_NO = ['一', '二', '三', '四', '五', '六', '七', '八']
const SEC_NAME: Record<string, string> = {
  basic: '基本資料',
  acct: '帳號資訊',
  holder: '使用人資訊',
  scope: '權限內容',
  attach: '附件（公文）',
  expiry: '有效期限',
  preview: '簽核路徑預覽'
}

const route = useRoute()
const router = useRouter()
const { toast } = useToast()
const { codes, me, userName, can, ensureCodes } = useSession()

const today = fmtDate(new Date())
const form = reactive(emptyForm())
const ready = ref(false)
const submitting = ref(false)
const showAssetRef = ref(false)
const banner = ref('')
/** 帳號類別、名稱、系統是否鎖定（對既有帳號異動、或非新增單的重新送出） */
const identityLocked = ref(false)
const docFile = ref<File | null>(null)
const docInput = ref<HTMLInputElement | null>(null)
const docReadFailed = ref(false)
/** 重送：原申請日期、版本號、目前有效的公文 */
const origApplyDate = ref('')
const versionNo = ref(0)
const oldDocs = ref<Attachment[]>([])

function q(name: string): string {
  const v = route.query[name]
  return typeof v === 'string' ? v : ''
}
const resubmitId = q('resubmit')
const queryType = q('reqType')
const queryAcct = q('acctId')
const queryAcctName = q('acctName')
const changeType: ReqTypeCode | null = !resubmitId && isReqType(queryType) && queryType !== 'ADD' && queryAcct ? queryType : null

const isDoor = computed(() => form.catgId === DOOR_CATG_ID)
// 門禁卡新增：卡號待關卡 6 登記，表單不顯示帳號名稱欄（spec 4.9）
const doorAdd = computed(() => isDoor.value && form.reqTypeCode === 'ADD')
/** 停用、刪除：只能填申請單位與原因 */
const contentLocked = computed(() => isReasonOnly(form.reqTypeCode))
const applyDateText = computed(() => (resubmitId ? dateOnly(origApplyDate.value) || today : today))
const assetIsTrigger = computed(() => isReviewTriggerAsset(codes.value, form.assetClassId))
const suggestExpiry = computed(() => suggestExpiryOf(codes.value, form.catgId))
const sortedCatgs = computed(() => [...(codes.value?.categories ?? [])].sort((a, b) => a.sortSeq - b.sortSeq))
/** 重送（含新增）與異動一律鎖定帳號類別；後端新增重送也以原申請單類別為準 */
const catgLocked = computed(() => identityLocked.value || !!resubmitId)
/** 新增時只列開放新增的類別；類別鎖定時列全部（原類別已關閉也能顯示原值） */
const catgOptions = computed((): Category[] => {
  if (catgLocked.value || form.reqTypeCode !== 'ADD') return sortedCatgs.value
  return sortedCatgs.value.filter(c => c.enabled || c.catgId === form.catgId)
})
const catgDescr = computed(() => sortedCatgs.value.find(c => c.catgId === form.catgId)?.catgDescr ?? '')
const triggerIds = computed(() => {
  const ids = (codes.value?.assetClasses ?? []).filter(a => a.reviewTrigger && a.selectable).map(a => a.assetClassId)
  return ids.length ? ids.join('／') : '—'
})
/** 只列可選碼；帳號目前的碼若已不可選也列出（標「不可選用」），避免畫面顯示空白 */
const assetGroups = computed(() => {
  const shown = (codes.value?.assetClasses ?? []).filter(a => a.selectable || a.assetClassId === form.assetClassId)
  const groups: { label: string; items: AssetClass[] }[] = [
    { label: '硬體類', items: shown.filter(a => a.assetTypeCode === 'H') },
    { label: '軟體類', items: shown.filter(a => a.assetTypeCode === 'S') }
  ]
  const other = shown.filter(a => a.assetTypeCode !== 'H' && a.assetTypeCode !== 'S')
  if (other.length) groups.push({ label: '其他', items: other })
  return groups.filter(g => g.items.length > 0)
})
const preview = computed(() => buildPreview(codes.value))
const sections = computed(() => ['basic', 'acct', 'holder', 'scope', 'attach', 'expiry', 'preview'].filter(k => k !== 'expiry' || !contentLocked.value))

function secTitle(key: string): string {
  const i = sections.value.indexOf(key)
  return `${SEC_NO[i] ?? ''}、${SEC_NAME[key]}`
}

const typeHint = computed(() => {
  if (resubmitId) return '重新送出不可變更申請類型與帳號類別，僅能修改申請內容。'
  if (changeType) return '申請類型已依您點選的操作鎖定，不可更改。'
  return '「修改」「停用」「重新啟用」「刪除」請從帳號列表或帳號申請單列表點選對應帳號的操作按鈕發起，此處僅能新增全新帳號。'
})

const reasonText = computed((): { label: string; placeholder: string } => {
  switch (form.reqTypeCode) {
    case 'DELETE':
      return { label: reasonFieldName('DELETE'), placeholder: '請填寫刪除原因' }
    case 'DISABLE':
      return { label: reasonFieldName('DISABLE'), placeholder: '請填寫停用原因（此動作可逆，之後可申請重新啟用）' }
    case 'ENABLE':
      return { label: '申請原因（重新啟用之業務需求）', placeholder: '請說明重新啟用之業務需求，並確認權限範圍與有效期限' }
    case 'MODIFY':
      return { label: '申請原因（請說明異動前後差異）', placeholder: '請填寫修改原因' }
    default:
      return { label: '申請原因', placeholder: '請填寫申請原因' }
  }
})

// 類別切換：門禁卡不填帳號名稱（卡號待執行人登記）、資產分類碼鎖未分類、權限固定 DOOR；切回一般類別時權限回預設
watch(
  () => form.catgId,
  (now, before) => {
    if (now === DOOR_CATG_ID) {
      if (!identityLocked.value) form.acctName = ''
      form.assetClassId = ''
      form.holderCntct = ''
      form.accessLevelCode = 'DOOR'
    } else if (before === DOOR_CATG_ID) {
      if (!identityLocked.value) form.acctName = ''
      form.accessLevelCode = 'RO'
    }
  }
)

// 切回永久有效時清空到期日，避免殘留舊日期造成混淆
watch(
  () => form.expiryTypeCode,
  v => {
    if (v !== 'DATE') form.expiryDate = ''
  }
)

/** 「同申請人」（spec 8.3） */
function copyApplicant(): void {
  form.holderName = userName.value
  if (isDoor.value) {
    form.holderEmail = form.applyEmail.trim() || (me.value?.email ?? '') || form.holderEmail
  } else {
    form.holderCntct = form.applyTelNo.trim() || form.applyEmail.trim() || (me.value?.telNo ?? '') || form.holderCntct
  }
  // 只有所屬單位空白時才帶入申請單位，避免覆蓋已手動填寫的不同值
  if (!form.holderDept.trim() && form.applyDept.trim()) form.holderDept = form.applyDept.trim()
}

function clearDoc(): void {
  docFile.value = null
  if (docInput.value) docInput.value.value = ''
}

/** 選檔即檢查副檔名與大小；不符提示並清掉選擇；空檔視同未上傳 */
function onDocFile(ev: Event): void {
  const f = (ev.target as HTMLInputElement).files?.[0] ?? null
  if (!f) {
    docFile.value = null
    return
  }
  const err = checkAttachFile(f)
  if (err) {
    toast(err, 'amber')
    clearDoc()
    return
  }
  docFile.value = f
}

function cancel(): void {
  void router.push(can('ACC_LIST_FULL') ? '/accounts' : '/requests')
}

/** 重送遇 409（版本不符）時重抓版本號，表單內容保留 */
async function reloadVersion(): Promise<void> {
  try {
    const req = await getRequest(resubmitId)
    versionNo.value = req.versionNo
  } catch {
    // 重抓失敗不影響已顯示的訊息
  }
}

async function send(file: File | null): Promise<void> {
  submitting.value = true
  try {
    const payload = toPayload(form)
    if (resubmitId) {
      await resubmitRequest(resubmitId, { ...payload, versionNo: versionNo.value }, file)
      toast(`已修改內容並重新送出，${resubmitId} 回到關卡 2 重新審核`, 'teal')
    } else {
      const r = await submitRequest(payload, file)
      toast(`【${REQ_TYPE_LABEL[form.reqTypeCode]}】申請已送出！單號 ${r?.reqId ?? ''}`, 'teal')
    }
    docReadFailed.value = false
    void router.push(can('ACC_LIST_FULL') ? '/accounts' : '/requests')
  } catch (e) {
    toast(errorMessage(e, '送出失敗，請稍後再試'), 'amber')
    if (resubmitId && errorStatus(e) === 409) void reloadVersion()
  } finally {
    submitting.value = false
  }
}

async function submit(): Promise<void> {
  if (submitting.value) return
  const err = validateRequestForm(form, today)
  if (err) {
    toast(err, 'amber')
    return
  }
  let file: File | null = null
  if (docFile.value) {
    try {
      file = await readAttachFile(docFile.value)
    } catch {
      // D17：公文讀取失敗 → U07，使用者確認後不附公文送出
      docReadFailed.value = true
      return
    }
  }
  await send(file)
}

function sendWithoutDoc(): void {
  clearDoc()
  void send(null)
}

onMounted(async () => {
  try {
    await ensureCodes()
  } catch (e) {
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
    return
  }
  const firstCatg = sortedCatgs.value.find(c => c.enabled)?.catgId ?? ''
  try {
    if (resubmitId) {
      const req = await getRequest(resubmitId)
      identityLocked.value = req.reqTypeCode !== 'ADD'
      Object.assign(form, formFromRequest(req))
      origApplyDate.value = req.applyDate ?? ''
      versionNo.value = req.versionNo
      oldDocs.value = (req.attachments ?? []).filter(a => a.attachTypeCode === 'DOC')
      banner.value = `此申請（單號：${resubmitId}）先前被退件，退件原因：${req.rejectReason || '（無法取得退件原因）'}。請依此修改下方內容後重新送出，將回到關卡 2 重新審核。`
    } else if (changeType) {
      const acct = await getLookupAccount(queryAcct, queryAcctName)
      identityLocked.value = true
      Object.assign(form, formFromAccount(acct, changeType))
      banner.value = `您正在為現有帳號「${acct.acctName || acct.acctId}」（單號：${acct.acctId}）建立【${REQ_TYPE_LABEL[changeType]}】申請，帳號類別、名稱與系統已鎖定不可修改。`
    } else {
      Object.assign(form, emptyForm(firstCatg))
    }
    ready.value = true
  } catch (e) {
    // 帶入資料失敗：不讓使用者對空白內容送出
    toast(errorMessage(e, '查詢失敗，請稍後再試'), 'amber')
  }
})
</script>

<style scoped>
.top { margin: 0 0 10px; }
.door-fields { margin-bottom: 4px; }
.door-fields .hint { margin-top: 4px; }
.btn-link.inline { font-size: 17px; min-height: 0; padding: 0 4px; }
option.direct { color: #b5691a; font-weight: 700; }
input.sample { background: #f1f5f9; color: var(--gray); }
.apply-contact-hint { margin-top: 0; }
.delete-warn { margin: 0 0 12px; font-weight: 700; }
.doc-read-fail { margin: 4px 0 0; }
</style>
