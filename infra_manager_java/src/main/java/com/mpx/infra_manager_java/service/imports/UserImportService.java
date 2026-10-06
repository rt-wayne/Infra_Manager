package com.mpx.infra_manager_java.service.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：舊系統 users.json 匯入 IM_USER／IM_USER_ROLE_MAP（S2 回合二，裁示 ①A 修訂、②A）。
//           規則：帳號去空白轉小寫（保留中間空白）當 LOGIN_ID；工號由對照表決定，對照表沒有的帳號整筆略過並列在摘要；
//           全員 PWD_HASH 一律重設為「帳號小寫」的 bcrypt、IS_DFLT_PWD=1（已改過密碼的人也重設；預設密碼不受 6 字規則限制）；
//           '' → NULL；STATUS 依 active（缺省視為啟用）；角色以 IM_ROLE 主檔為準，未知角色整批失敗；
//           既有工號走 UPDATE，角色對照新增／重新啟用／不再出現者設 STATUS=0；
//           所有檢核先做完再寫 DB，寫入在一個交易內（AppTransactionConfig），任何例外整批回滾。
//           欄位長度依 V1 DDL：USER_ID 30 byte、LOGIN_ID 64 byte、EMAIL 255 byte、USER_NAME／JOB_TITLE／DEPT_NAME／TEL 50 字
// ============================================================

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mpx.infra_manager_java.dao.imports.UserImportDao;
import com.mpx.infra_manager_java.model.auth.UserRow;
import com.mpx.infra_manager_java.model.imports.ImportMapping;
import com.mpx.infra_manager_java.model.imports.ImportSummary;
import com.mpx.infra_manager_java.model.imports.LegacyUser;
import com.mpx.infra_manager_java.model.imports.UserRoleRow;

@Service
public class UserImportService {

	private static final Logger log = LoggerFactory.getLogger(UserImportService.class);

	static final int USER_ID_MAX_BYTES = 30;
	static final int LOGIN_ID_MAX_BYTES = 64;
	static final int EMAIL_MAX_BYTES = 255;
	static final int NAME_MAX_CHARS = 50;
	static final int TITLE_MAX_CHARS = 50;
	static final int DEPT_MAX_CHARS = 50;
	static final int TEL_MAX_CHARS = 50;
	/** 預載的停用代用帳號，匯入不得覆寫 */
	static final String RESERVED_USER_ID = "HIST_PAPER";

	private final UserImportDao dao;
	private final PasswordEncoder passwordEncoder;

	public UserImportService(UserImportDao dao, PasswordEncoder passwordEncoder) {
		this.dao = dao;
		this.passwordEncoder = passwordEncoder;
	}

	/** 檢核完全部資料後才開始寫；回摘要，任何問題丟 UserImportException（交易回滾） */
	@Transactional
	public ImportSummary importUsers(List<LegacyUser> users, ImportMapping mapping) {
		List<String> problems = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		List<Prepared> prepared = prepare(users, mapping, problems, skipped);
		checkRoles(prepared, problems);
		Map<String, UserRow> existing = checkAgainstDb(prepared, problems);
		if (!problems.isEmpty()) {
			throw new UserImportException(problems);
		}

		int inserted = 0;
		int updated = 0;
		int rolesAdded = 0;
		int rolesDisabled = 0;
		for (Prepared p : prepared) {
			p.row.setPwdHash(passwordEncoder.encode(p.row.getLoginId()));
			if (existing.containsKey(p.row.getUserId())) {
				expectOne(dao.updateUser(p.row), "更新 IM_USER " + p.row.getUserId());
				updated++;
			} else {
				expectOne(dao.insertUser(p.row), "新增 IM_USER " + p.row.getUserId());
				inserted++;
			}
			Map<String, Integer> current = new HashMap<>();
			for (UserRoleRow r : dao.findRoles(p.row.getUserId())) {
				current.put(r.getRoleId(), r.getStatus());
			}
			for (String roleId : p.roles) {
				Integer status = current.get(roleId);
				if (status == null) {
					expectOne(dao.insertRole(p.row.getUserId(), roleId), "新增角色 " + roleId);
					rolesAdded++;
				} else if (status != 1) {
					expectOne(dao.setRoleStatus(p.row.getUserId(), roleId, 1), "啟用角色 " + roleId);
					rolesAdded++;
				}
			}
			for (Map.Entry<String, Integer> e : current.entrySet()) {
				if (!p.roles.contains(e.getKey()) && Integer.valueOf(1).equals(e.getValue())) {
					expectOne(dao.setRoleStatus(p.row.getUserId(), e.getKey(), 0), "停用角色 " + e.getKey());
					rolesDisabled++;
				}
			}
			log.info("匯入使用者 user={} roles={}", p.row.getUserId(), p.roles.size());
		}
		return new ImportSummary(inserted, updated, rolesAdded, rolesDisabled, skipped);
	}

	/** 正規化與不碰 DB 的檢核：帳號、對照、欄位長度、檔內重複 */
	private static List<Prepared> prepare(List<LegacyUser> users, ImportMapping mapping, List<String> problems,
			List<String> skipped) {
		List<Prepared> result = new ArrayList<>();
		Set<String> seenLogin = new HashSet<>();
		Map<String, String> loginByUserId = new HashMap<>();
		for (int i = 0; i < users.size(); i++) {
			LegacyUser u = users.get(i);
			String where = "第 " + (i + 1) + " 筆";
			if (u == null || u.id() == null || u.id().isBlank()) {
				problems.add(where + " id 空白");
				continue;
			}
			String loginId = u.id().trim().toLowerCase(Locale.ROOT);
			where += "（" + loginId + "）";
			if (!seenLogin.add(loginId)) {
				problems.add(where + " 帳號在檔內重複");
				continue;
			}
			if (bytes(loginId) > LOGIN_ID_MAX_BYTES) {
				problems.add(where + " 帳號超過 " + LOGIN_ID_MAX_BYTES + " bytes");
				continue;
			}
			String userId = mapping.userIdOf(loginId).orElse(null);
			if (userId == null) {
				skipped.add(loginId);
				continue;
			}
			if (bytes(userId) > USER_ID_MAX_BYTES) {
				problems.add(where + " 工號超過 " + USER_ID_MAX_BYTES + " bytes");
				continue;
			}
			if (RESERVED_USER_ID.equalsIgnoreCase(userId)) {
				problems.add(where + " 工號不得是保留帳號 " + RESERVED_USER_ID);
				continue;
			}
			String other = loginByUserId.putIfAbsent(userId, loginId);
			if (other != null) {
				problems.add(where + " 與帳號 " + other + " 對到同一個工號 " + userId);
				continue;
			}
			UserRow row = new UserRow();
			row.setUserId(userId);
			row.setLoginId(loginId);
			row.setUserName(emptyToNull(u.name()));
			row.setEmail(emptyToNull(u.email()));
			row.setJobTitle(emptyToNull(u.title()));
			row.setDeptName(emptyToNull(u.department()));
			row.setTel(emptyToNull(u.phone()));
			row.setStatus(Boolean.FALSE.equals(u.active()) ? 0 : 1);
			row.setIsDfltPwd(1);
			int before = problems.size();
			if (row.getUserName() == null) {
				problems.add(where + " 姓名空白");
			} else if (chars(row.getUserName()) > NAME_MAX_CHARS) {
				problems.add(where + " 姓名超過 " + NAME_MAX_CHARS + " 字");
			}
			if (row.getEmail() != null && bytes(row.getEmail()) > EMAIL_MAX_BYTES) {
				problems.add(where + " email 超過 " + EMAIL_MAX_BYTES + " bytes");
			}
			if (row.getJobTitle() != null && chars(row.getJobTitle()) > TITLE_MAX_CHARS) {
				problems.add(where + " 職稱超過 " + TITLE_MAX_CHARS + " 字");
			}
			if (row.getDeptName() != null && chars(row.getDeptName()) > DEPT_MAX_CHARS) {
				problems.add(where + " 部門超過 " + DEPT_MAX_CHARS + " 字");
			}
			if (row.getTel() != null && chars(row.getTel()) > TEL_MAX_CHARS) {
				problems.add(where + " 電話超過 " + TEL_MAX_CHARS + " 字");
			}
			Set<String> roles = new LinkedHashSet<>();
			for (String r : u.roles() == null ? List.<String>of() : u.roles()) {
				if (r == null || r.isBlank()) {
					problems.add(where + " 角色清單含空白值");
				} else {
					roles.add(r.trim());
				}
			}
			if (problems.size() == before) {
				result.add(new Prepared(row, List.copyOf(roles)));
			}
		}
		return result;
	}

	/** 角色一律要在 IM_ROLE 主檔（不分啟用狀態） */
	private void checkRoles(List<Prepared> prepared, List<String> problems) {
		Set<String> known = new HashSet<>(dao.findAllRoleIds());
		for (Prepared p : prepared) {
			for (String roleId : p.roles) {
				if (!known.contains(roleId)) {
					problems.add("帳號 " + p.row.getLoginId() + " 的角色 " + roleId + " 不在 IM_ROLE");
				}
			}
		}
	}

	/** 既有資料對照：帳號若已被別的工號使用，整批失敗（LOGIN_ID 唯一鍵） */
	private Map<String, UserRow> checkAgainstDb(List<Prepared> prepared, List<String> problems) {
		Map<String, UserRow> byUserId = new HashMap<>();
		Map<String, String> userIdByLogin = new HashMap<>();
		for (UserRow row : dao.findAllUsers()) {
			byUserId.put(row.getUserId(), row);
			userIdByLogin.put(row.getLoginId(), row.getUserId());
		}
		for (Prepared p : prepared) {
			String holder = userIdByLogin.get(p.row.getLoginId());
			if (holder != null && !holder.equals(p.row.getUserId())) {
				problems.add("帳號 " + p.row.getLoginId() + " 已屬於另一個工號，無法指派給 " + p.row.getUserId());
			}
		}
		return byUserId;
	}

	private static void expectOne(int affected, String action) {
		if (affected != 1) {
			throw new UserImportException(action + " 影響 " + affected + " 列（預期 1）");
		}
	}

	private static String emptyToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private static int bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8).length;
	}

	private static int chars(String s) {
		return s.codePointCount(0, s.length());
	}

	private record Prepared(UserRow row, List<String> roles) {
	}
}
