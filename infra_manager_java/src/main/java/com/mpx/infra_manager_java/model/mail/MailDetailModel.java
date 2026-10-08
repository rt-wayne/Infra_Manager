package com.mpx.infra_manager_java.model.mail;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-08
// 變更說明: 新增：信件「申請單明細」片段的顯示資料（S8 R2，照舊系統 mailer.js renderDetailSection 的欄位）。
//           全部是已排版好的字串，樣板只用 th:text 印出（禁 th:utext），不在樣板裡做任何邏輯。
//           用 getter 類別而不是 record：樣板表達式 ${d.appId} 走 getter 查找，record 的 appId() 不保證被找到
// ============================================================

import java.util.List;

public class MailDetailModel {

	private final String appId;
	private final String title;
	private final String prioName;
	private final String applicantName;
	private final String applicantDept;
	private final String applicantTel;
	private final String applicantEmail;
	private final String verAndFlow;
	private final String workSubj;
	private final String execMode;
	private final String supplier;
	private final String categories;
	private final String reasons;
	private final String scopes;
	private final String schedule;
	private final String location;
	private final String workDetail;
	private final String impactDesc;
	private final String riskDesc;
	private final String rollBackPlan;
	private final List<String> equipments;
	private final List<String> planSteps;
	private final String resubMemo;

	public MailDetailModel(String appId, String title, String prioName, String applicantName, String applicantDept,
			String applicantTel, String applicantEmail, String verAndFlow, String workSubj, String execMode,
			String supplier, String categories, String reasons, String scopes, String schedule, String location,
			String workDetail, String impactDesc, String riskDesc, String rollBackPlan, List<String> equipments,
			List<String> planSteps, String resubMemo) {
		this.appId = appId;
		this.title = title;
		this.prioName = prioName;
		this.applicantName = applicantName;
		this.applicantDept = applicantDept;
		this.applicantTel = applicantTel;
		this.applicantEmail = applicantEmail;
		this.verAndFlow = verAndFlow;
		this.workSubj = workSubj;
		this.execMode = execMode;
		this.supplier = supplier;
		this.categories = categories;
		this.reasons = reasons;
		this.scopes = scopes;
		this.schedule = schedule;
		this.location = location;
		this.workDetail = workDetail;
		this.impactDesc = impactDesc;
		this.riskDesc = riskDesc;
		this.rollBackPlan = rollBackPlan;
		this.equipments = equipments == null ? List.of() : List.copyOf(equipments);
		this.planSteps = planSteps == null ? List.of() : List.copyOf(planSteps);
		this.resubMemo = resubMemo;
	}

	public String getAppId() { return appId; }
	public String getTitle() { return title; }
	public String getPrioName() { return prioName; }
	public String getApplicantName() { return applicantName; }
	public String getApplicantDept() { return applicantDept; }
	public String getApplicantTel() { return applicantTel; }
	public String getApplicantEmail() { return applicantEmail; }
	public String getVerAndFlow() { return verAndFlow; }
	public String getWorkSubj() { return workSubj; }
	public String getExecMode() { return execMode; }
	public String getSupplier() { return supplier; }
	public String getCategories() { return categories; }
	public String getReasons() { return reasons; }
	public String getScopes() { return scopes; }
	public String getSchedule() { return schedule; }
	public String getLocation() { return location; }
	public String getWorkDetail() { return workDetail; }
	public String getImpactDesc() { return impactDesc; }
	public String getRiskDesc() { return riskDesc; }
	public String getRollBackPlan() { return rollBackPlan; }
	public List<String> getEquipments() { return equipments; }
	public List<String> getPlanSteps() { return planSteps; }
	public String getResubMemo() { return resubMemo; }
}
