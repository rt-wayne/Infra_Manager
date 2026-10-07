package com.mpx.infra_manager_java.model.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：檢視頁回應（S4），對應舊系統檢視頁各區塊：基本資料、事件分級、作業類別、異動作業內容、
//           檢核表、實際執行紀錄、簽核欄、附件、歷史版次、事件紀錄與 9 個權限旗標。
//           代碼欄位回代碼、前端對應中文；選項名稱（優先等級、類別、原因、範圍、檢核項、執行結果）由伺服器帶出。
//           日期時間一律 yyyy-MM-dd HH:mm 字串、日期 yyyy-MM-dd；附件不帶檔案路徑，下載走專用端點
//           S6 回合二 a（Claude Opus 5.5，2026-10-06）：加 rowVerNo（樂觀鎖，編輯草稿時帶回）與 Option.formOptionId
//           S9 R2（Claude Opus 5.5，2026-10-07）：approval 後面加 approvalHistory（目前實例以外的歷次簽核，依實例建立先後）
// ============================================================

import java.math.BigDecimal;
import java.util.List;

public record AppDetail(String appId, String title, String prioCode, String prioName, String prioColor,
		String statusCode, String sourceCode, String flowId, String flowName, Integer verNo, Long rowVerNo,
		Applicant applicant,
		String applyDate, boolean selfExec, boolean supplierExec, String workModeCode, String remoteMethod,
		Supplier supplier, String workSubject, String impactDesc, String workDetail, String riskDesc,
		String rollbackPlan, List<Option> categories, List<Option> reasons, String otherReason, List<Option> scopes,
		List<Equipment> equipments, List<PlanStep> planSteps, Schedule schedule, Location location, String resubmitMemo,
		List<CheckItem> checklist, Execution execution, Approval approval, List<PastApproval> approvalHistory,
		List<Attachment> attachments,
		List<Version> versions, List<Event> events, AppPermissions permissions, String createdAt, String updatedAt) {

	public record Applicant(String userId, String name, String deptName, String tel, String email) {
	}

	public record Supplier(String name, String contact, String tel, Integer headCount) {
	}

	/** groupCode：CATG／CATG_ITEM／REASON／SCOPE；upCode 只有 CATG_ITEM 有（所屬類別代碼）；otherText 只有 CATG 的「其他」補充有 */
	public record Option(Long formOptionId, String groupCode, String code, String name, String upCode,
			String otherText) {
	}

	public record Equipment(Integer seqNo, String name, String assetNo, String modelNo, String serialNo,
			String purpose, String mgmtIp) {
	}

	public record PlanStep(Integer seqNo, String text) {
	}

	public record Schedule(String start, String end, BigDecimal estHours) {
	}

	public record Location(String sourceCode, String areaName, String rackName, String uRange, String siteId,
			String rackId, Integer uStart, Integer uEnd, String omitReason) {
	}

	/** executor：執行人姓名（系統帳號）或手填的執行人描述，二擇一 */
	public record CheckItem(Integer seqNo, String code, String name, boolean done, String doneAt, String executor) {
	}

	public record Execution(Integer verNo, String actualStart, String actualEnd, String resultCode, String resultName,
			boolean exception, String exceptionDesc, boolean followUp, String followUpDesc, String memo,
			String executorName, String closedAt) {
	}

	/** apprId 為 null 表示沒有簽核實例（草稿、已收回），steps 是流程定義展開、狀態一律 WAITING */
	public record Approval(Long apprId, String statusCode, String startedAt, String closedAt, List<Step> steps) {
	}

	/**
	 * 歷次簽核的一個實例（目前那筆以外：已退件、已撤回、已取消）；verNo 是該實例所屬的申請單版次。
	 * 關卡的 candidateNames 一律空清單（實例已結束，候選人名單不再有意義）
	 */
	public record PastApproval(Long apprId, Integer verNo, String statusCode, String startedAt, String closedAt,
			List<Step> steps) {
	}

	public record Step(Integer seqNo, String stepCode, String stepName, String stepMode, boolean notifyOnly,
			String statusCode, String deciderName, String decidedAt, String memo, List<String> candidateNames) {
	}

	public record Attachment(Long attachId, String ownerType, String ownerId, String fileName, Long byteQty,
			String mimeType, String uploadedAt) {
	}

	public record Version(Integer verNo, String closeStatusCode, String reason, String snapAt) {
	}

	public record Event(Long eventId, Integer verNo, String code, String userName, String at, String memo) {
	}
}
