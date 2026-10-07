package com.mpx.infra_manager_java.service.template;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：範本列表、檢視、新增、修改、軟刪除（S5 R1）。
//           權限（第 43 項裁示 A）：任何登入者可看、可新增；修改與刪除限建立者本人或 admin。
//           建立者在新增後不會變更，所以「先讀列判權限、再條件式 UPDATE（STATUS=1）」沒有競態；0 列＝期間被刪 → 404。
//           表單內容以 AppDraftValidator 同一套規則檢查並正規化（標題可空），再序列化成 FORM_JSON；
//           序列化用本類別專屬的 JsonMapper，避免全域 ObjectMapper 設定日後改變存進 DB 的格式。
//           不做樂觀鎖（IM_TMPL 沒有版本欄，同舊系統後寫者為準）
//           2026-10-07 R3：加 recordUse（建單交易內累計套用次數；範本不存在時略過、不擋建單）
// ============================================================

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.dao.template.TemplateDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppDraft;
import com.mpx.infra_manager_java.model.changerequest.AppDraftRequest;
import com.mpx.infra_manager_java.model.template.TemplateDetail;
import com.mpx.infra_manager_java.model.template.TemplateForm;
import com.mpx.infra_manager_java.model.template.TemplateListItem;
import com.mpx.infra_manager_java.model.template.TemplateRequest;
import com.mpx.infra_manager_java.model.template.TemplateRow;
import com.mpx.infra_manager_java.service.changerequest.AppDraftValidator;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.util.TextLength;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TemplateService {

	public static final String MSG_NOT_FOUND = "找不到範本，可能已被刪除";
	public static final String MSG_NO_NAME = "請填寫範本名稱";
	public static final String MSG_NO_FORM = "缺少範本內容";
	public static final String MSG_FORBIDDEN = "只有範本建立者或系統管理員可以修改或刪除範本";

	/** TMPL_NAME 為 VARCHAR2(100 CHAR) */
	static final int MAX_NAME = 100;

	/** 新系統產生的 tpl_<毫秒>_<4 位 hex>，與舊系統沿用的 tpl_xxx 都符合；TMPL_ID 為 VARCHAR2(60) */
	private static final Pattern TMPL_ID = Pattern.compile("^[A-Za-z0-9_-]{1,60}$");

	private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

	private static final JsonMapper MAPPER = JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private final TemplateDao templateDao;
	private final FormOptionDao formOptionDao;
	private final SecureRandom random = new SecureRandom();

	public TemplateService(TemplateDao templateDao, FormOptionDao formOptionDao) {
		this.templateDao = templateDao;
		this.formOptionDao = formOptionDao;
	}

	public List<TemplateListItem> list(AuthUser me) {
		return templateDao.findActive().stream()
				.map(r -> new TemplateListItem(r.getTmplId(), r.getTmplName(), r.getPrioCode(), r.getPrioName(),
						r.getPrioColor(), r.getOwnerName(), useCnt(r), TaiwanTime.formatDateTime(r.getLastUseDate()),
						r.getLastUseUserName(), updatedAt(r), canEdit(r, me)))
				.toList();
	}

	public TemplateDetail get(String tmplId, AuthUser me) {
		TemplateRow r = require(tmplId);
		return new TemplateDetail(r.getTmplId(), r.getTmplName(), readForm(r.getTmplId(), r.getFormJson()), r.getOwnerName(),
				useCnt(r), TaiwanTime.formatDateTime(r.getLastUseDate()), r.getLastUseUserName(),
				TaiwanTime.formatDateTime(r.getCreateDate()), updatedAt(r), canEdit(r, me));
	}

	/** 新增，回新範本 ID */
	public String create(TemplateRequest request, AuthUser me) {
		Validated v = validate(request);
		String tmplId = newId();
		templateDao.insert(tmplId, v.name(), v.formJson(), me.userId());
		log.info("範本新增 tmplId={} userId={}", tmplId, me.userId());
		return tmplId;
	}

	public void update(String tmplId, TemplateRequest request, AuthUser me) {
		TemplateRow r = require(tmplId);
		requireEditable(r, me);
		Validated v = validate(request);
		if (templateDao.update(tmplId, v.name(), v.formJson(), me.userId()) == 0) {
			throw new ApiNotFoundException(MSG_NOT_FOUND);
		}
		log.info("範本修改 tmplId={} userId={}", tmplId, me.userId());
	}

	public void delete(String tmplId, AuthUser me) {
		TemplateRow r = require(tmplId);
		requireEditable(r, me);
		if (templateDao.softDelete(tmplId, me.userId()) == 0) {
			throw new ApiNotFoundException(MSG_NOT_FOUND);
		}
		log.info("範本刪除 tmplId={} userId={}", tmplId, me.userId());
	}

	/**
	 * 用範本建單成功時累計套用次數；由 AppDraftService.create 在建單交易內呼叫。
	 * ID 格式不對或範本已被刪除時只記 info、不中斷建單（草稿內容已由前端帶入，範本存不存在不影響這張單）
	 */
	public void recordUse(String tmplId, AuthUser me) {
		if (tmplId == null || !TMPL_ID.matcher(tmplId).matches()) {
			log.info("範本套用略過（ID 格式不符） userId={}", me.userId());
			return;
		}
		if (templateDao.recordUse(tmplId, me.userId()) == 0) {
			log.info("範本套用略過（範本不存在或已刪除） tmplId={} userId={}", tmplId, me.userId());
		}
	}

	static boolean canEdit(TemplateRow r, AuthUser me) {
		return me.userId().equals(r.getOwnerUserId()) || me.roles().contains("admin");
	}

	private TemplateRow require(String tmplId) {
		if (tmplId == null || !TMPL_ID.matcher(tmplId).matches()) {
			throw new ApiNotFoundException(MSG_NOT_FOUND);
		}
		TemplateRow r = templateDao.findById(tmplId);
		if (r == null) {
			throw new ApiNotFoundException(MSG_NOT_FOUND);
		}
		return r;
	}

	private static void requireEditable(TemplateRow r, AuthUser me) {
		if (!canEdit(r, me)) {
			log.warn("範本修改或刪除被拒 tmplId={} userId={}", r.getTmplId(), me.userId());
			throw new AccessDeniedException(MSG_FORBIDDEN);
		}
	}

	private record Validated(String name, String formJson) {
	}

	private Validated validate(TemplateRequest request) {
		if (request == null) {
			throw new ApiBadRequestException("請求格式錯誤");
		}
		String name = request.tmplName() == null ? null : request.tmplName().strip();
		if (name == null || name.isEmpty()) {
			throw new ApiBadRequestException(MSG_NO_NAME);
		}
		name = TextLength.check("tmplName", "範本名稱", name, MAX_NAME);
		if (request.form() == null) {
			throw new ApiBadRequestException(MSG_NO_FORM);
		}
		AppDraft draft = AppDraftValidator.validate(toDraftRequest(request.form()), formOptionDao.findActive(), false);
		return new Validated(name, MAPPER.writeValueAsString(fromDraft(draft)));
	}

	static AppDraftRequest toDraftRequest(TemplateForm f) {
		AppDraftRequest.Schedule schedule = f.schedule() == null ? null
				: new AppDraftRequest.Schedule(null, null, f.schedule().estHours());
		return new AppDraftRequest(f.title(), f.prioCode(), null, f.selfExec(), f.supplierExec(), f.workModeCode(),
				f.remoteMethod(), f.supplier(), f.workSubject(), f.impactDesc(), f.workDetail(), f.riskDesc(),
				f.rollbackPlan(), f.categoryItemIds(), f.categoryOthers(), f.reasonIds(), f.otherReason(),
				f.scopeIds(), f.equipments(), f.planSteps(), schedule, f.location(), null);
	}

	/** 正規化後的草稿內容轉回範本格式（空白已轉 null、空白列已去掉） */
	static TemplateForm fromDraft(AppDraft d) {
		AppDraftRequest.Supplier supplier = d.supName() == null && d.supContact() == null && d.supTel() == null
				&& d.supHeadCount() == null ? null
						: new AppDraftRequest.Supplier(d.supName(), d.supContact(), d.supTel(), d.supHeadCount());
		return new TemplateForm(d.title(), d.prioCode(), d.selfExec(), d.supplierExec(), d.workModeCode(),
				d.remoteMethod(), supplier, d.workSubject(), d.impactDesc(), d.workDetail(), d.riskDesc(),
				d.rollbackPlan(), d.categoryItemIds(),
				d.categoryOthers().stream().map(o -> new AppDraftRequest.CategoryOther(o.formOptionId(), o.text()))
						.toList(),
				d.reasonIds(), d.otherReason(), d.scopeIds(),
				d.equipments().stream().map(e -> new AppDraftRequest.Equipment(e.name(), e.assetNo(), e.modelNo(),
						e.serialNo(), e.purpose(), e.mgmtIp())).toList(),
				d.planSteps(), d.estHours() == null ? null : new TemplateForm.Schedule(d.estHours()),
				d.omitReason() == null ? null : new AppDraftRequest.Location(d.omitReason()));
	}

	/** FORM_JSON 解析失敗（理論上只有匯入資料格式不對）時記 warn、回 null，讓列表與其他欄位仍能顯示 */
	private static TemplateForm readForm(String tmplId, String json) {
		if (json == null) {
			return null;
		}
		try {
			return MAPPER.readValue(json, TemplateForm.class);
		} catch (JacksonException e) {
			log.warn("範本 FORM_JSON 解析失敗 tmplId={} {}", tmplId, e.getClass().getSimpleName());
			return null;
		}
	}

	private String newId() {
		byte[] b = new byte[2];
		random.nextBytes(b);
		return "tpl_" + System.currentTimeMillis() + "_" + HexFormat.of().formatHex(b);
	}

	private static int useCnt(TemplateRow r) {
		return r.getUseCnt() == null ? 0 : r.getUseCnt();
	}

	private static String updatedAt(TemplateRow r) {
		return TaiwanTime.formatDateTime(r.getUpdateDate() != null ? r.getUpdateDate() : r.getCreateDate());
	}
}
