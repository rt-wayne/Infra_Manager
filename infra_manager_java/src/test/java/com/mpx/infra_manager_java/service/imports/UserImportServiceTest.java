package com.mpx.infra_manager_java.service.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：使用者匯入規則單元測試（S2 回合二匯入器）。
//           DAO 用 Mockito，PasswordEncoder 用真的 DelegatingPasswordEncoder（驗證寫入的雜湊確實對得上「帳號小寫」）。
//           驗證：對照表沒有的帳號略過且不寫 DB；未知角色、欄位過長、檔內重複、帳號被別的工號佔用 → 整批失敗且不寫；
//           新增走 insert、既有走 update，兩者都 IS_DFLT_PWD=1、雜湊可用帳號小寫比對；角色新增／重啟／停用；'' → NULL；
//           active=false → STATUS 0；影響列數不是 1 丟例外
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mpx.infra_manager_java.dao.imports.UserImportDao;
import com.mpx.infra_manager_java.model.auth.UserRow;
import com.mpx.infra_manager_java.model.imports.ImportMapping;
import com.mpx.infra_manager_java.model.imports.ImportSummary;
import com.mpx.infra_manager_java.model.imports.LegacyUser;
import com.mpx.infra_manager_java.model.imports.UserRoleRow;

class UserImportServiceTest {

	private final UserImportDao dao = mock(UserImportDao.class);
	private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
	private final UserImportService service = new UserImportService(dao, encoder);

	@BeforeEach
	void setUp() {
		when(dao.findAllRoleIds()).thenReturn(List.of("admin", "it_manager", "dept_manager", "idc_admin", "governance", "infra"));
		when(dao.findAllUsers()).thenReturn(List.of(existing("HIST_PAPER", "hist_paper", 0)));
		when(dao.findRoles(anyString())).thenReturn(List.of());
		when(dao.insertUser(any())).thenReturn(1);
		when(dao.updateUser(any())).thenReturn(1);
		when(dao.insertRole(anyString(), anyString())).thenReturn(1);
		when(dao.setRoleStatus(anyString(), anyString(), anyInt())).thenReturn(1);
	}

	@Test
	void 新帳號新增並以帳號小寫的bcrypt當預設密碼() {
		LegacyUser wayne = user("Wayne", "Wayne (測試)", "wayne@example.com", "工程師", "資訊部", "", List.of("admin"), true);

		ImportSummary summary = service.importUsers(List.of(wayne), mapping("wayne", "T0001"));

		assertThat(summary.inserted()).isEqualTo(1);
		assertThat(summary.updated()).isZero();
		assertThat(summary.rolesAdded()).isEqualTo(1);
		assertThat(summary.skipped()).isEmpty();
		ArgumentCaptor<UserRow> captor = ArgumentCaptor.forClass(UserRow.class);
		verify(dao).insertUser(captor.capture());
		UserRow row = captor.getValue();
		assertThat(row.getUserId()).isEqualTo("T0001");
		assertThat(row.getLoginId()).isEqualTo("wayne");
		assertThat(row.getUserName()).isEqualTo("Wayne (測試)");
		assertThat(row.getTel()).as("'' 轉 NULL").isNull();
		assertThat(row.getStatus()).isEqualTo(1);
		assertThat(row.getIsDfltPwd()).isEqualTo(1);
		assertThat(row.getPwdHash()).startsWith("{bcrypt}");
		assertThat(encoder.matches("wayne", row.getPwdHash())).isTrue();
		assertThat(encoder.matches("Wayne", row.getPwdHash())).isFalse();
		verify(dao).insertRole("T0001", "admin");
		verify(dao, never()).updateUser(any());
	}

	@Test
	void 既有工號更新並重設密碼_角色新增重啟停用() {
		when(dao.findAllUsers()).thenReturn(List.of(existing("A001", "gary", 1)));
		UserRoleRow adminOn = role("admin", 1);
		UserRoleRow infraOff = role("infra", 0);
		UserRoleRow itOn = role("it_manager", 1);
		when(dao.findRoles("A001")).thenReturn(List.of(adminOn, infraOff, itOn));
		LegacyUser gary = user("gary", "Gary", "g@example.com", "協理", "資訊部", "123",
				List.of("admin", "infra", "governance"), true);

		ImportSummary summary = service.importUsers(List.of(gary), mapping("gary", "A001"));

		assertThat(summary.updated()).isEqualTo(1);
		assertThat(summary.inserted()).isZero();
		assertThat(summary.rolesAdded()).as("governance 新增 + infra 重啟").isEqualTo(2);
		assertThat(summary.rolesDisabled()).as("it_manager 停用").isEqualTo(1);
		ArgumentCaptor<UserRow> captor = ArgumentCaptor.forClass(UserRow.class);
		verify(dao).updateUser(captor.capture());
		assertThat(captor.getValue().getIsDfltPwd()).isEqualTo(1);
		assertThat(encoder.matches("gary", captor.getValue().getPwdHash())).isTrue();
		verify(dao).insertRole("A001", "governance");
		verify(dao).setRoleStatus("A001", "infra", 1);
		verify(dao).setRoleStatus("A001", "it_manager", 0);
		verify(dao, never()).setRoleStatus(eq("A001"), eq("admin"), anyInt());
		verify(dao, never()).insertUser(any());
	}

	@Test
	void 對照表沒有的帳號略過並列在摘要_其餘照常匯入() {
		LegacyUser known = user("alex", "Alex", null, null, null, null, List.of(), true);
		LegacyUser unknown = user("Alan Kuo", "Alan", null, null, null, null, List.of("infra"), true);

		ImportSummary summary = service.importUsers(List.of(unknown, known), mapping("alex", "A002"));

		assertThat(summary.skipped()).containsExactly("alan kuo");
		assertThat(summary.inserted()).isEqualTo(1);
		verify(dao, never()).insertRole(anyString(), eq("infra"));
	}

	@Test
	void 帳號保留中間空白並轉小寫_active_false存停用() {
		LegacyUser alan = user(" Alan Kuo ", "Alan", null, null, null, null, List.of(), false);

		service.importUsers(List.of(alan), mapping("alan kuo", "A003"));

		ArgumentCaptor<UserRow> captor = ArgumentCaptor.forClass(UserRow.class);
		verify(dao).insertUser(captor.capture());
		assertThat(captor.getValue().getLoginId()).isEqualTo("alan kuo");
		assertThat(captor.getValue().getStatus()).isZero();
	}

	@Test
	void 未知角色整批失敗且不寫DB() {
		LegacyUser ok = user("gary", "Gary", null, null, null, null, List.of("admin"), true);
		LegacyUser bad = user("alex", "Alex", null, null, null, null, List.of("superuser", "admin"), true);

		assertThatThrownBy(() -> service.importUsers(List.of(ok, bad), mapping("gary", "A001", "alex", "A002")))
				.isInstanceOf(UserImportException.class)
				.hasMessageContaining("alex")
				.hasMessageContaining("superuser");
		verifyNothingWritten();
	}

	@Test
	void 檢核問題一次全列_欄位過長_姓名空白_檔內重複_同工號_角色空白() {
		LegacyUser tooLong = user("u1", "名".repeat(51), "a".repeat(250) + "@x.com.tw", "職".repeat(51), "部".repeat(51),
				"1".repeat(51), List.of("admin", " "), true);
		LegacyUser noName = user("u2", "  ", null, null, null, null, List.of(), true);
		LegacyUser dup = user("U1", "Dup", null, null, null, null, List.of(), true);
		LegacyUser sameId = user("u3", "Same", null, null, null, null, List.of(), true);

		assertThatThrownBy(() -> service.importUsers(List.of(tooLong, noName, dup, sameId),
				mapping("u1", "A001", "u2", "A002", "u3", "A002")))
				.isInstanceOf(UserImportException.class)
				.satisfies(e -> {
					List<String> problems = ((UserImportException) e).getProblems();
					assertThat(problems).hasSize(9);
					assertThat(problems).anyMatch(p -> p.contains("姓名超過"));
					assertThat(problems).anyMatch(p -> p.contains("email 超過"));
					assertThat(problems).anyMatch(p -> p.contains("職稱超過"));
					assertThat(problems).anyMatch(p -> p.contains("部門超過"));
					assertThat(problems).anyMatch(p -> p.contains("電話超過"));
					assertThat(problems).anyMatch(p -> p.contains("角色清單含空白值"));
					assertThat(problems).anyMatch(p -> p.contains("第 2 筆（u2） 姓名空白"));
					assertThat(problems).anyMatch(p -> p.contains("第 3 筆（u1） 帳號在檔內重複"));
					assertThat(problems).anyMatch(p -> p.contains("第 4 筆（u3） 與帳號 u2 對到同一個工號 A002"));
				});
		verifyNothingWritten();
	}

	@Test
	void 帳號已屬於別的工號時整批失敗() {
		when(dao.findAllUsers()).thenReturn(List.of(existing("A001", "gary", 1)));
		LegacyUser gary = user("gary", "Gary", null, null, null, null, List.of(), true);

		assertThatThrownBy(() -> service.importUsers(List.of(gary), mapping("gary", "A999")))
				.isInstanceOf(UserImportException.class)
				.hasMessageContaining("gary")
				.hasMessageContaining("A999");
		verifyNothingWritten();
	}

	@Test
	void 工號不得是HIST_PAPER_與超長工號() {
		LegacyUser a = user("a", "A", null, null, null, null, List.of(), true);
		LegacyUser b = user("b", "B", null, null, null, null, List.of(), true);

		assertThatThrownBy(() -> service.importUsers(List.of(a, b), mapping("a", "hist_paper", "b", "X".repeat(31))))
				.isInstanceOf(UserImportException.class)
				.hasMessageContaining("HIST_PAPER")
				.hasMessageContaining("30 bytes");
		verifyNothingWritten();
	}

	@Test
	void id空白與角色為null都能處理() {
		LegacyUser blankId = new LegacyUser("  ", "X", null, null, null, null, null, null);
		LegacyUser nullRoles = new LegacyUser("ok", "Ok", null, null, null, null, null, null);

		assertThatThrownBy(() -> service.importUsers(List.of(blankId), mapping("ok", "A001")))
				.isInstanceOf(UserImportException.class)
				.hasMessageContaining("第 1 筆 id 空白");
		ImportSummary summary = service.importUsers(List.of(nullRoles), mapping("ok", "A001"));
		assertThat(summary.inserted()).isEqualTo(1);
		assertThat(summary.rolesAdded()).isZero();
	}

	@Test
	void 影響列數不是1時丟例外() {
		when(dao.insertUser(any())).thenReturn(0);
		LegacyUser a = user("a", "A", null, null, null, null, List.of(), true);

		assertThatThrownBy(() -> service.importUsers(List.of(a), mapping("a", "A001")))
				.isInstanceOf(UserImportException.class)
				.hasMessageContaining("影響 0 列");
	}

	private void verifyNothingWritten() {
		verify(dao, never()).insertUser(any());
		verify(dao, never()).updateUser(any());
		verify(dao, never()).insertRole(anyString(), anyString());
		verify(dao, never()).setRoleStatus(anyString(), anyString(), anyInt());
	}

	private static LegacyUser user(String id, String name, String email, String title, String dept, String phone,
			List<String> roles, Boolean active) {
		return new LegacyUser(id, name, email, title, dept, phone, roles, active);
	}

	private static ImportMapping mapping(String... pairs) {
		Map<String, String> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			m.put(pairs[i], pairs[i + 1]);
		}
		return new ImportMapping(m);
	}

	private static UserRow existing(String userId, String loginId, int status) {
		UserRow row = new UserRow();
		row.setUserId(userId);
		row.setLoginId(loginId);
		row.setStatus(status);
		return row;
	}

	private static UserRoleRow role(String roleId, int status) {
		UserRoleRow row = new UserRoleRow();
		row.setRoleId(roleId);
		row.setStatus(status);
		return row;
	}
}
