package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：填寫執行紀錄（S10 R1，施工計畫 ①～⑦、⑫）。單一交易：
//           鎖外驗單號格式（不符 404）、rowVerNo 必填 → 便宜預檢（findLockState：單不存在 404；不是 idc_admin 也不是申請人 403，
//           不取鎖）→ ExecutionValidator 檢格式（送治理審查時另檢必填）→ 檢核項執行人工號須為啟用使用者 →
//           AppWriteDao.lockForUpdate 只比版本取鎖（0 列：單不存在 404、其餘 409 版本過期）→
//           鎖內重讀：狀態不是 APPROVED／IN_EXECUTION 409（修舊系統可重開已結案單）、權限重判 403 →
//           該版次第一次儲存時依啟用中的 CHECK_LIST 選項（SORT_NO 順序）展開檢核表 → 逐列更新本文帶到的檢核項
//           （未知序號 400；完成沒填時間補伺服器現在時間）→ 執行結果 upsert（送治理審查時寫結案人與結案時間）→
//           主檔狀態改 PENDING_REVIEW（有結果）或 IN_EXECUTION（暫存）。不寫事件（⑩：DDL 沒有 EXECUTE 事件碼）
// ============================================================

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mpx.infra_manager_java.dao.auth.UserDao;
import com.mpx.infra_manager_java.dao.changerequest.AppDao;
import com.mpx.infra_manager_java.dao.changerequest.AppWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.ExecWriteDao;
import com.mpx.infra_manager_java.dao.changerequest.FormOptionDao;
import com.mpx.infra_manager_java.model.auth.AuthUser;
import com.mpx.infra_manager_java.model.changerequest.AppLockRow;
import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.model.changerequest.ExecutionDraft;
import com.mpx.infra_manager_java.model.changerequest.ExecutionRequest;
import com.mpx.infra_manager_java.model.changerequest.FormOptionRow;
import com.mpx.infra_manager_java.util.TaiwanTime;
import com.mpx.infra_manager_java.web.ApiBadRequestException;
import com.mpx.infra_manager_java.web.ApiConflictException;
import com.mpx.infra_manager_java.web.ApiNotFoundException;

@Service
public class AppExecutionService {

	public static final String MSG_EXEC_FORBIDDEN = "只有機房管理員或申請人可以填寫執行紀錄";
	public static final String MSG_EXEC_NOT_EXECUTABLE = "申請單不在待執行或執行中，無法填寫執行紀錄，請重新載入頁面";
	public static final String MSG_BAD_EXECUTOR = "檢核項執行人不是系統中啟用的使用者";
	static final String ROLE_IDC_ADMIN = "idc_admin";
	static final String GROUP_CHECK_LIST = "CHECK_LIST";
	static final String GROUP_EXEC_RESULT = "EXEC_RESULT";
	static final Set<String> EXECUTABLE = Set.of("APPROVED", "IN_EXECUTION");

	private static final Pattern APP_ID = Pattern.compile("^[A-Z0-9-]{1,20}$");

	/** 儲存結果：新的 ROW_VER_NO 與儲存後的主檔狀態 */
	public record SaveResult(long rowVerNo, String statusCode) {
	}

	private final AppDao appDao;
	private final AppWriteDao appWriteDao;
	private final ExecWriteDao execWriteDao;
	private final FormOptionDao formOptionDao;
	private final UserDao userDao;

	public AppExecutionService(AppDao appDao, AppWriteDao appWriteDao, ExecWriteDao execWriteDao,
			FormOptionDao formOptionDao, UserDao userDao) {
		this.appDao = appDao;
		this.appWriteDao = appWriteDao;
		this.execWriteDao = execWriteDao;
		this.formOptionDao = formOptionDao;
		this.userDao = userDao;
	}

	@Transactional
	public SaveResult save(String appId, ExecutionRequest request, AuthUser me) {
		long rowVerNo = requireVersion(appId, request == null ? null : request.rowVerNo());

		// 鎖外便宜預檢：沒有權限的人不取鎖、不加 ROW_VER_NO
		AppLockRow state = appWriteDao.findLockState(appId);
		if (state == null) {
			throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
		}
		if (!mayExecute(me, state.getApplyUserId())) {
			throw new AccessDeniedException(MSG_EXEC_FORBIDDEN);
		}
		List<FormOptionRow> options = formOptionDao.findActive();
		Set<String> resultCodes = options.stream().filter(o -> GROUP_EXEC_RESULT.equals(o.getGroupCode()))
				.map(FormOptionRow::getOptionCode).collect(Collectors.toSet());
		ExecutionDraft draft = ExecutionValidator.validate(request, resultCodes);
		Set<String> executors = new LinkedHashSet<>();
		draft.checklist().forEach(i -> {
			if (i.userId() != null) {
				executors.add(i.userId());
			}
		});
		for (String userId : executors) {
			if (!userDao.isActive(userId)) {
				throw new ApiBadRequestException(MSG_BAD_EXECUTOR);
			}
		}

		if (appWriteDao.lockForUpdate(appId, rowVerNo, me.userId()) == 0) {
			if (appWriteDao.findLockState(appId) == null) {
				throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
			}
			throw new ApiConflictException(AppFlowService.MSG_STALE);
		}

		// 鎖內依現況重判狀態與權限
		AppRow app = appDao.findById(appId)
				.orElseThrow(() -> new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND));
		if (!EXECUTABLE.contains(app.getAppStatusCode())) {
			throw new ApiConflictException(MSG_EXEC_NOT_EXECUTABLE);
		}
		if (!mayExecute(me, app.getApplyUserId())) {
			throw new AccessDeniedException(MSG_EXEC_FORBIDDEN);
		}
		int verNo = app.getCurrVerNo() == null ? 1 : app.getCurrVerNo();

		Set<Integer> known = new HashSet<>(execWriteDao.findCheckSeqNos(appId, verNo));
		if (known.isEmpty()) {
			List<FormOptionRow> items = options.stream().filter(o -> GROUP_CHECK_LIST.equals(o.getGroupCode()))
					.toList();
			for (int i = 0; i < items.size(); i++) {
				execWriteDao.insertCheckItem(appId, verNo, i + 1, items.get(i).getFormOptionId(), me.userId());
				known.add(i + 1);
			}
		}
		Timestamp now = Timestamp.valueOf(LocalDateTime.now(TaiwanTime.ZONE));
		for (ExecutionDraft.CheckItem item : draft.checklist()) {
			if (!known.contains(item.seqNo())) {
				throw new ApiBadRequestException(ExecutionValidator.MSG_BAD_SEQ);
			}
			Timestamp doneAt = !item.done() ? null : item.doneAt() != null ? item.doneAt() : now;
			execWriteDao.updateCheckItem(appId, verNo, item, doneAt, me.userId());
		}

		execWriteDao.upsertExec(appId, verNo, draft, draft.submit() ? me.userId() : null, me.userId());
		String toStatus = draft.submit() ? "PENDING_REVIEW" : "IN_EXECUTION";
		if (!toStatus.equals(app.getAppStatusCode())) {
			appWriteDao.updateStatus(appId, toStatus, me.userId());
		}
		return new SaveResult(rowVerNo + 1, toStatus);
	}

	/** 機房管理員或申請人本人（修舊系統執行不檢查角色，第 44 項） */
	static boolean mayExecute(AuthUser me, String applyUserId) {
		return me.roles().contains(ROLE_IDC_ADMIN) || me.userId().equals(applyUserId);
	}

	/** 單號格式不對視為找不到（404）；缺版本號 400 */
	private static long requireVersion(String appId, Long rowVerNo) {
		if (appId == null || !APP_ID.matcher(appId).matches()) {
			throw new ApiNotFoundException(AppQueryService.MSG_APP_NOT_FOUND);
		}
		if (rowVerNo == null) {
			throw new ApiBadRequestException(AppDraftService.MSG_NO_VERSION);
		}
		return rowVerNo;
	}
}
