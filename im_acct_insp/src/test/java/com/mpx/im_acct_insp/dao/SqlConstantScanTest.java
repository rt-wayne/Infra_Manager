package com.mpx.im_acct_insp.dao;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：SQL 常數與代碼常數掃描（取代 ExistingUserTableSqlTest；spec v2.0 §1.5、§3、Table v0.2 第 11 節）
//           所有 DAO 的 SQL 常數：不得引用既有模組 IM_USER 或任何 RD_USER. 限定名稱、不得用 SYSDATE（業務時間由 Clock 綁定）、
//           不得出現已廢除的代碼（EXTEND、VOID、CONFIRM、NA、TO_EXEC、EXTEND_DONE、FLAG_CLEAR、IMPORT）與已刪欄位
//           （ORIG_REQ_ID、ORIG_ACCT_ID、NEW_ACCT_ID、IMPORT_ID、DISABLE_TYPE_CODE、REVIEW_BASIS_CODE、IS_DIRECT、IS_SHOW、
//           APPLY_TEL_NO、APPLY_EMAIL、INSP_STATE_CODE 欄位寫入）；AcctConst 的字串常數也不得含已廢除代碼；
//           Controller 除 POST /auth/login 外每個 API 方法都宣告 Identity（＝都驗 token）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: UPDATE_EXEC_DONE 須含門禁卡新增時申請單 ACCT_NAME 寫卡號
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S2：掃描清單加 EmgDao、EmergencyController；API 數 32 → 35；
//           緊急存取人名取 IM_ACCT_USER（申請人／處理人／結案人三個 LEFT JOIN）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: 新增：DAO SQL 常數的 CASE END 不得與下一個字黏在一起（EmgDao 曾拼出 ENDAS，S3 瀏覽器實測發現）；
//           掃描清單 DAOS 須與 dao 套件的 @Repository 類別一致（防新增 DAO 漏列）
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4a：API 數 35 → 36（POST /emergencies/{id}/report）；EmgDao.UPDATE_REPORT 須帶樂觀鎖與前置條件
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-13
// 變更說明: BACKLOG 第 46 項 S4b-1：API 數 36 → 38（/approve、/return）；EmgDao.UPDATE_DECIDE 樂觀鎖與不看 DUE_DATE；
//           AcctHistDao 寫入帶 EMG_ID、查詢 COALESCE 兩種來源單的申請人；AcctDao.UPDATE_REVIEW_FLAG 欄位
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.controller.AccountController;
import com.mpx.im_acct_insp.controller.AttachmentController;
import com.mpx.im_acct_insp.controller.AuthController;
import com.mpx.im_acct_insp.controller.EmergencyController;
import com.mpx.im_acct_insp.controller.HomeController;
import com.mpx.im_acct_insp.controller.InspectionController;
import com.mpx.im_acct_insp.controller.JobController;
import com.mpx.im_acct_insp.controller.RequestController;

class SqlConstantScanTest {

	static final List<Class<?>> DAOS = List.of(AcctDao.class, AcctHistDao.class, AcctReqDao.class, AdminLogDao.class,
			AttachDao.class, CodeDao.class, DocSeqDao.class, EmgDao.class, HomeDao.class, InspDao.class, JobRunDao.class,
			LoginTokenDao.class, NotifyLogDao.class, PermDao.class, UserDao.class);

	static final List<Class<?>> CONTROLLERS = List.of(AccountController.class, AttachmentController.class, AuthController.class,
			EmergencyController.class, HomeController.class, InspectionController.class, JobController.class,
			RequestController.class);

	/** 既有模組使用者表（IM_ACCT_USER 不算） */
	static final Pattern IM_USER = Pattern.compile("(?<![\\w])IM_USER\\b");
	static final Pattern RD_USER = Pattern.compile("\\bRD_USER\\.");
	static final Pattern SYSDATE = Pattern.compile("\\bSYSDATE\\b");
	static final Pattern ABOLISHED_CODE = Pattern.compile("'(EXTEND|VOID|CONFIRM|NA|TO_EXEC|EXTEND_DONE|FLAG_CLEAR|IMPORT\\w*)'");
	static final Pattern ABOLISHED_COLUMN = Pattern.compile(
			"\\b(ORIG_REQ_ID|ORIG_ACCT_ID|NEW_ACCT_ID|IMPORT_ID|DISABLE_TYPE_CODE|REVIEW_BASIS_CODE|IS_DIRECT|IS_SHOW|APPLY_TEL_NO|APPLY_EMAIL|EXCPT_CNT\\s*,\\s*INSP_STATE_CODE)\\b");
	/** END 後面直接接英文字母（END_DATE 之類有底線的欄名不算） */
	static final Pattern GLUED_END = Pattern.compile("\\bEND[A-Z]");

	static Map<String, String> constants(List<Class<?>> classes) throws IllegalAccessException {
		Map<String, String> m = new LinkedHashMap<>();
		for (Class<?> c : classes) {
			for (Field f : c.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers()) && f.getType() == String.class) {
					f.setAccessible(true);
					m.put(c.getSimpleName() + "." + f.getName(), (String) f.get(null));
				}
			}
		}
		return m;
	}

	@Test
	void DAO_SQL常數_不引用既有模組使用者表_不用SYSDATE_無已廢除代碼與欄位() throws Exception {
		Map<String, String> sqls = constants(DAOS);
		assertThat(sqls).hasSizeGreaterThan(60);
		List<String> bad = new ArrayList<>();
		for (Map.Entry<String, String> e : sqls.entrySet()) {
			String s = e.getValue();
			if (IM_USER.matcher(s).find()) {
				bad.add(e.getKey() + " 引用 IM_USER");
			}
			if (RD_USER.matcher(s).find()) {
				bad.add(e.getKey() + " 使用 RD_USER. 限定名稱");
			}
			if (SYSDATE.matcher(s).find()) {
				bad.add(e.getKey() + " 使用 SYSDATE");
			}
			if (ABOLISHED_CODE.matcher(s).find()) {
				bad.add(e.getKey() + " 含已廢除代碼");
			}
			if (ABOLISHED_COLUMN.matcher(s).find()) {
				bad.add(e.getKey() + " 含已刪欄位");
			}
		}
		assertThat(bad).isEmpty();
	}

	/** 掃描清單 DAOS 須涵蓋 dao 套件所有 @Repository（新增 DAO 忘了列進來就整支不會被掃） */
	@Test
	void 掃描清單涵蓋dao套件所有Repository() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Repository.class));
		List<String> found = scanner.findCandidateComponents(SqlConstantScanTest.class.getPackageName()).stream()
				.map(BeanDefinition::getBeanClassName).sorted().toList();
		List<String> listed = DAOS.stream().map(Class::getName).sorted().toList();
		assertThat(found).hasSizeGreaterThan(10).isEqualTo(listed);
	}

	/** CASE 的 END 與下一個字黏在一起（例 ENDAS）：text block 串接片段時前導空白被剝掉，Oracle 回 ORA-00905 */
	@Test
	void DAO_SQL常數_END不與下一個字黏在一起() throws Exception {
		List<String> bad = new ArrayList<>();
		for (Map.Entry<String, String> e : constants(DAOS).entrySet()) {
			if (GLUED_END.matcher(e.getValue()).find()) {
				bad.add(e.getKey());
			}
		}
		assertThat(bad).isEmpty();
	}

	@Test
	void 人員姓名一律取IM_ACCT_USER或快照() {
		assertThat(AcctReqDao.FIND_BY_ID).contains("LEFT JOIN IM_ACCT_USER UA ON UA.USER_ID = R.APPLY_BY");
		assertThat(AcctHistDao.FIND_BY_ACCT).contains("H.ACT_BY_NAME").doesNotContain("IM_ACCT_USER");
		assertThat(InspDao.INSP_SUMMARY).contains("I.INSP_BY_NAME").doesNotContain("IM_ACCT_USER");
		assertThat(EmgDao.SELECT_BASE).contains("LEFT JOIN IM_ACCT_USER UA ON UA.USER_ID = E.APPLY_BY")
				.contains("LEFT JOIN IM_ACCT_USER UD ON UD.USER_ID = E.DECIDE_BY")
				.contains("LEFT JOIN IM_ACCT_USER UC ON UC.USER_ID = E.CLOSE_BY");
	}

	@Test
	void 緊急存取_狀態即時計算_時間綁參數_列表與明細共用() {
		assertThat(EmgDao.STATE_EXPR).contains(":now").doesNotContain("SYSDATE");
		assertThat(EmgDao.FIND_LIST).contains(EmgDao.STATE_EXPR).contains(":applyBy IS NULL OR X.APPLY_BY = :applyBy");
		assertThat(EmgDao.FIND_BY_ID).contains(EmgDao.STATE_EXPR).contains("E.EMG_ID = :emgId");
		assertThat(EmgDao.INSERT_EMG).contains("VERSION_NO").contains(":dueDate").doesNotContain("DECIDE_CODE");
	}

	@Test
	void 樂觀鎖_狀態轉移都帶版本與預期狀態並遞增版本() {
		for (String sql : List.of(AcctReqDao.UPDATE_APPROVE, AcctReqDao.UPDATE_REJECT, AcctReqDao.UPDATE_EXEC_DONE,
				AcctReqDao.UPDATE_VERIFY_OK, AcctReqDao.UPDATE_VERIFY_REJECT, AcctReqDao.UPDATE_RESUBMIT)) {
			assertThat(sql).contains("VERSION_NO = :versionNo").contains("VERSION_NO = VERSION_NO + 1")
					.contains("REQ_STATE_CODE =").contains("CURR_STAGE_ID");
		}
		assertThat(AcctReqDao.UPDATE_EXEC_DONE).contains("EFFECT_DATE = NVL(EFFECT_DATE, :now)")
				// 門禁卡新增：申請單 ACCT_NAME 與 CARD_NO 同條件寫卡號
				.contains("ACCT_NAME = CASE WHEN :setCard = 1 THEN :cardNo ELSE ACCT_NAME END");
		assertThat(AcctReqDao.UPDATE_REJECT).contains("REJECT_CNT = REJECT_CNT + 1");
		assertThat(AcctReqDao.UPDATE_VERIFY_REJECT).contains("VERIFY_REJECT_CNT = VERIFY_REJECT_CNT + 1");
		assertThat(AcctReqDao.UPDATE_RESUBMIT).doesNotContain("APPLY_DATE");
		// 緊急存取回報（S4a）：帶版本、限本人、限尚未回報且主管未處理
		assertThat(EmgDao.UPDATE_REPORT).contains("VERSION_NO = :versionNo").contains("VERSION_NO = VERSION_NO + 1")
				.contains("APPLY_BY = :applyBy").contains("REPORT_TYPE_CODE IS NULL").contains("DECIDE_CODE IS NULL")
				.contains("STATUS = 1");
		// 緊急存取主管處理（S4b）：帶版本、限尚未處理；逾期後仍可處理所以不看 DUE_DATE
		assertThat(EmgDao.UPDATE_DECIDE).contains("VERSION_NO = :versionNo").contains("VERSION_NO = VERSION_NO + 1")
				.contains("DECIDE_CODE IS NULL").contains("STATUS = 1").contains("IS_SELF_DECIDE = :isSelfDecide")
				.doesNotContain("DUE_DATE");
	}

	@Test
	void 帳號歷史_寫入帶EMG_ID_查詢取申請單或緊急存取單申請人() {
		assertThat(AcctHistDao.INSERT_HIST).contains("EMG_ID").contains(":emgId");
		assertThat(AcctHistDao.FIND_BY_ACCT).contains("LEFT JOIN IM_EMG_ACCESS EM ON EM.EMG_ID = H.EMG_ID")
				.contains("COALESCE(R.APPLY_BY, EM.APPLY_BY) AS REQ_APPLY_BY");
		assertThat(AcctDao.UPDATE_REVIEW_FLAG).contains("IS_REVIEW_FLAG = 1").contains("REVIEW_FLAG_SOURCE_CODE = :sourceCode")
				.contains("REVIEW_FLAG_BY = :flagBy").doesNotContain("SYSDATE");
	}

	@Test
	void 代碼常數_無已廢除值() throws Exception {
		List<String> abolished = List.of("EXTEND", "VOID", "CONFIRM", "NA", "EXTEND_DONE", "FLAG_CLEAR", "AC", "AI",
				"EM", "RD_USER.IM_USER");
		for (Map.Entry<String, String> e : constants(List.of(AcctConst.class)).entrySet()) {
			assertThat(abolished).as(e.getKey()).doesNotContain(e.getValue());
		}
		assertThat(AcctConst.REQ_TYPES).containsExactly("ADD", "MODIFY", "DISABLE", "ENABLE", "DELETE");
		assertThat(AcctConst.REQ_STATES).containsExactly("REVIEW", "EXEC", "VERIFY", "APPROVED", "REJECTED");
		assertThat(AcctConst.ACCT_STATES).containsExactly("REVIEW", "APPROVED", "EXPIRED", "DISABLED", "REJECTED", "DELETED");
		assertThat(AcctConst.RESULT_CODES).containsExactly("NORMAL", "EXCPT");
		assertThat(AcctConst.STAGE_EXEC).isEqualTo(6);
		assertThat(AcctConst.STAGE_VERIFY).isEqualTo(7);
	}

	@Test
	void Controller_除登入外每個API都宣告Identity() {
		List<String> missing = new ArrayList<>();
		int apis = 0;
		for (Class<?> c : CONTROLLERS) {
			for (Method m : c.getDeclaredMethods()) {
				boolean api = m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class)
						|| m.isAnnotationPresent(PutMapping.class) || m.isAnnotationPresent(DeleteMapping.class)
						|| m.isAnnotationPresent(RequestMapping.class);
				if (!api) {
					continue;
				}
				apis++;
				boolean login = c == AuthController.class && m.getName().equals("login");
				boolean hasId = List.of(m.getParameterTypes()).contains(Identity.class);
				if (login == hasId) {
					missing.add(c.getSimpleName() + "." + m.getName());
				}
			}
		}
		assertThat(apis).isEqualTo(38);
		assertThat(missing).isEmpty();
	}
}
