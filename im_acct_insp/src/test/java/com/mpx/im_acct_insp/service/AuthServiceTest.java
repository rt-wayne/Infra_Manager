package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 新增：登入、token 時效、切換身分單元測試（spec v2.0 §2.1、§2.5、§6.2；驗收 T01、T02、T13）
//           登入：工號不存在／停用仍做雜湊計算後 A01、鎖定 A02（即使密碼正確）、密碼錯累加失敗次數、成功發 token（DB 只存 SHA-256）；
//           修改：查詢後才被鎖定（成功或失敗計數 UPDATE 影響 0 筆）→ A02、不發 token、不補 dummyHash；
//           token：未帶／亂碼／已登出／閒置超過 30 分／登入超過 8 小時／使用者停用皆 401 A03，LAST_ACCESS_DATE 每 60 秒最多寫一次，
//           代理身分停用自動切回；切換身分：無 SYS_IMPERSONATE 403 並寫 DENY、不可切自己、切換與軌跡同一交易、
//           切換中仍以實際使用者權限判斷可改切別人；切回未切換中 409 E20；/me 回 telNo、email（有效身分）
// ============================================================

import static com.mpx.im_acct_insp.service.RequestServiceTest.assertStatus;
import static com.mpx.im_acct_insp.support.TestFixture.GARY;
import static com.mpx.im_acct_insp.support.TestFixture.HANK;
import static com.mpx.im_acct_insp.support.TestFixture.KEVIN;
import static com.mpx.im_acct_insp.support.TestFixture.NOW;
import static com.mpx.im_acct_insp.support.TestFixture.TESTER;
import static com.mpx.im_acct_insp.support.TestFixture.id;
import static com.mpx.im_acct_insp.support.TestFixture.impersonate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.common.PasswordHasher;
import com.mpx.im_acct_insp.common.TokenCodec;
import com.mpx.im_acct_insp.dao.AdminLogDao;
import com.mpx.im_acct_insp.dao.LoginTokenDao;
import com.mpx.im_acct_insp.dao.UserDao;
import com.mpx.im_acct_insp.model.AcctUser;
import com.mpx.im_acct_insp.model.LoginResult;
import com.mpx.im_acct_insp.model.LoginToken;
import com.mpx.im_acct_insp.model.MeInfo;
import com.mpx.im_acct_insp.model.request.ImpersonateForm;
import com.mpx.im_acct_insp.model.request.LoginForm;
import com.mpx.im_acct_insp.support.TestFixture;

class AuthServiceTest {

	TestFixture fx;
	UserDao userDao;
	LoginTokenDao tokenDao;
	AdminLogDao adminLogDao;
	PasswordHasher hasher;
	TokenIdentityProvider provider;
	AuthService auth;

	@BeforeEach
	void setUp() {
		fx = new TestFixture();
		userDao = mock(UserDao.class);
		tokenDao = mock(LoginTokenDao.class);
		adminLogDao = mock(AdminLogDao.class);
		hasher = mock(PasswordHasher.class);
		provider = new TokenIdentityProvider(tokenDao, userDao, fx.permissionService, fx.clock);
		auth = new AuthService(userDao, tokenDao, adminLogDao, hasher, new TokenCodec(), provider, fx.permissionService, fx.tx,
				fx.clock);
		for (String u : TestFixture.NAMES.keySet()) {
			when(userDao.findById(u)).thenReturn(user(u, 1));
			when(fx.permDao.findPerms(u)).thenReturn(new ArrayList<>(TestFixture.permsOf(u)));
			when(fx.permDao.findRoleIds(u)).thenReturn(TestFixture.USER_ROLES.get(u));
			when(fx.permDao.findExecCatgs(u)).thenReturn(TestFixture.USER_EXEC.getOrDefault(u, List.of()));
		}
		when(tokenDao.setActAs(anyString(), any(), anyString(), any())).thenReturn(1);
		when(userDao.loginOk(anyString(), any())).thenReturn(1);
		when(userDao.loginFail(anyString(), org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(1);
	}

	// ------------------------------------------------------------
	// 登入
	// ------------------------------------------------------------

	@Test
	void 登入成功_回token_DB只存SHA256_失敗次數歸0() {
		when(hasher.verify(any(), eq("PBKDF2$h"))).thenReturn(true);
		LoginResult r = auth.login(login(KEVIN, "pw"));
		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(tokenDao).insert(hash.capture(), eq(KEVIN), eq(NOW.plusHours(8)), eq(NOW));
		assertThat(hash.getValue()).hasSize(64).isNotEqualTo(r.getToken()).isEqualTo(TokenCodec.sha256Hex(r.getToken()));
		verify(userDao).loginOk(KEVIN, NOW);
		assertThat(r.getMe().getUserId()).isEqualTo(KEVIN);
		assertThat(r.getMe().getPermissions()).contains("ACC_APPLY", "APPR_G4", "VERIFY_G7", "INSP_VIEW");
	}

	@Test
	void 登入_工號不存在_停用_無雜湊_皆仍做雜湊後A01() {
		assertStatus(() -> auth.login(login("9999999", "pw")), HttpStatus.UNAUTHORIZED, AcctMsg.A01);
		when(userDao.findById(TESTER)).thenReturn(user(TESTER, 0));
		assertStatus(() -> auth.login(login(TESTER, "pw")), HttpStatus.UNAUTHORIZED, AcctMsg.A01);
		AcctUser noPwd = user(GARY, 1);
		noPwd.setPwdHash(null);
		when(userDao.findById(GARY)).thenReturn(noPwd);
		assertStatus(() -> auth.login(login(GARY, "pw")), HttpStatus.UNAUTHORIZED, AcctMsg.A01);
		verify(hasher, org.mockito.Mockito.times(3)).dummyHash(any());
		verify(tokenDao, never()).insert(any(), any(), any(), any());
	}

	@Test
	void 登入_密碼錯累加失敗次數A01_已鎖定即使密碼正確也A02() {
		when(hasher.verify(any(), any())).thenReturn(false);
		assertStatus(() -> auth.login(login(KEVIN, "bad")), HttpStatus.UNAUTHORIZED, AcctMsg.A01);
		verify(userDao).loginFail(KEVIN, AcctConst.LOGIN_MAX_FAIL, NOW);

		AcctUser locked = user(TESTER, 1);
		locked.setLockDate(NOW.minusMinutes(1));
		when(userDao.findById(TESTER)).thenReturn(locked);
		when(hasher.verify(any(), any())).thenReturn(true);
		assertStatus(() -> auth.login(login(TESTER, "pw")), HttpStatus.UNAUTHORIZED, AcctMsg.A02);
		verify(tokenDao, never()).insert(any(), any(), any(), any());
	}

	@Test
	void 登入_查詢時未鎖定但成功更新影響0筆_A02且不發token() {
		when(hasher.verify(any(), eq("PBKDF2$h"))).thenReturn(true);
		when(userDao.loginOk(KEVIN, NOW)).thenReturn(0);
		assertStatus(() -> auth.login(login(KEVIN, "pw")), HttpStatus.UNAUTHORIZED, AcctMsg.A02);
		verify(tokenDao, never()).insert(any(), any(), any(), any());
		// 已做過一次真雜湊，不得再補 dummyHash（耗時會與其他路徑不同）
		verify(hasher, never()).dummyHash(any());
	}

	@Test
	void 登入_查詢時未鎖定但密碼錯且失敗計數影響0筆_A02() {
		when(hasher.verify(any(), any())).thenReturn(false);
		when(userDao.loginFail(KEVIN, AcctConst.LOGIN_MAX_FAIL, NOW)).thenReturn(0);
		assertStatus(() -> auth.login(login(KEVIN, "bad")), HttpStatus.UNAUTHORIZED, AcctMsg.A02);
		verify(hasher, never()).dummyHash(any());
		verify(userDao, never()).loginOk(any(), any());
	}

	@Test
	void 登入失敗計數與鎖定_同句UPDATE() {
		assertThat(UserDao.UPDATE_LOGIN_FAIL).contains("LOGIN_FAIL_CNT = LOGIN_FAIL_CNT + 1")
				.contains("CASE WHEN LOGIN_FAIL_CNT + 1 >= :maxFail THEN :now ELSE LOCK_DATE END").contains("LOCK_DATE IS NULL");
		assertThat(UserDao.UPDATE_LOGIN_OK).contains("LOCK_DATE IS NULL");
		assertThat(AcctConst.LOGIN_MAX_FAIL).isEqualTo(5);
	}

	// ------------------------------------------------------------
	// token 時效
	// ------------------------------------------------------------

	@Test
	void token有效條件() {
		assertThat(TokenIdentityProvider.isValid(token(NOW.minusMinutes(30), NOW.plusHours(1), null, 1), NOW)).isTrue();
		assertThat(TokenIdentityProvider.isValid(token(NOW.minusMinutes(31), NOW.plusHours(1), null, 1), NOW)).isFalse();
		assertThat(TokenIdentityProvider.isValid(token(NOW, NOW, null, 1), NOW)).isFalse();
		assertThat(TokenIdentityProvider.isValid(token(NOW, NOW.plusHours(1), NOW.minusMinutes(1), 1), NOW)).isFalse();
		assertThat(TokenIdentityProvider.isValid(token(NOW, NOW.plusHours(1), null, 0), NOW)).isFalse();
		assertThat(TokenIdentityProvider.isValid(null, NOW)).isFalse();
	}

	@Test
	void resolve_未帶亂碼查無皆401_A03() {
		assertStatus(() -> provider.resolve(null), HttpStatus.UNAUTHORIZED, AcctMsg.A03);
		assertStatus(() -> provider.resolve("Bearer garbage"), HttpStatus.UNAUTHORIZED, AcctMsg.A03);
		when(tokenDao.find(TokenCodec.sha256Hex("tok"))).thenReturn(token(NOW.minusMinutes(40), NOW.plusHours(1), null, 1));
		assertStatus(() -> provider.resolve("Bearer tok"), HttpStatus.UNAUTHORIZED, AcctMsg.A03);
	}

	@Test
	void resolve_節流寫LAST_ACCESS_代理身分停用自動切回() {
		String hash = TokenCodec.sha256Hex("tok");
		LoginToken t = token(NOW.minusSeconds(30), NOW.plusHours(1), null, 1);
		t.setUserId(HANK);
		when(tokenDao.find(hash)).thenReturn(t);
		Identity id = provider.resolve("Bearer tok");
		assertThat(id.getUserId()).isEqualTo(HANK);
		verify(tokenDao, never()).touch(any(), any());

		t.setLastAccessDate(NOW.minusSeconds(90));
		t.setActAsUserId(GARY);
		t.setActAsStatus(0);
		Identity back = provider.resolve("Bearer tok");
		verify(tokenDao).touch(hash, NOW);
		verify(tokenDao).setActAs(hash, null, HANK, NOW);
		assertThat(back.isImpersonating()).isFalse();
		verify(adminLogDao, never()).insert(any(), any(), any(), any(), any(), any(), any());

		t.setActAsStatus(1);
		Identity as = provider.resolve("Bearer tok");
		assertThat(as.isImpersonating()).isTrue();
		assertThat(as.getUserId()).isEqualTo(GARY);
		assertThat(as.getRealUserId()).isEqualTo(HANK);
		assertThat(as.getPermissions()).contains("APPR_G5").doesNotContain("SYS_IMPERSONATE");
	}

	// ------------------------------------------------------------
	// 切換身分
	// ------------------------------------------------------------

	@Test
	void 切換身分_無權限403並寫DENY() {
		ImpersonateForm f = target(GARY);
		assertStatus(() -> auth.impersonate(id(KEVIN), f), HttpStatus.FORBIDDEN, AcctMsg.P01);
		verify(adminLogDao).insert(NOW, KEVIN, "Kevin (李凱鈞)", AcctConst.ADM_IMPERSONATE, GARY, null, AcctConst.ADM_DENY);
		verify(tokenDao, never()).setActAs(any(), any(), any(), any());
	}

	@Test
	void 切換身分_成功_同一交易寫token與軌跡_不可切自己() {
		assertStatus(() -> auth.impersonate(id(HANK), target(HANK)), HttpStatus.BAD_REQUEST, AcctMsg.IMPERSONATE_SELF);
		MeInfo me = auth.impersonate(id(HANK), target(GARY));
		verify(tokenDao).setActAs("hash-" + HANK, GARY, HANK, NOW);
		verify(adminLogDao).insert(eq(NOW), eq(HANK), eq("Hank (黃志翰)"), eq(AcctConst.ADM_IMPERSONATE), eq(GARY),
				eq("{\"targetUserName\":\"Gary (許永成)\"}"), eq(AcctConst.ADM_SUCCESS));
		assertThat(fx.txm.commits).isEqualTo(1);
		assertThat(me.getUserId()).isEqualTo(GARY);
		assertThat(me.getRealUserId()).isEqualTo(HANK);
		assertThat(me.getImpersonating()).isTrue();
		assertThat(me.getCanImpersonate()).isFalse();
		assertThat(me.getEmail()).isEqualTo(GARY + "@example.com");
		assertThat(me.getTelNo()).isEqualTo("#" + GARY);
	}

	@Test
	void 切換中_以實際使用者權限判斷_可改切別人_也可查名單() {
		Identity asGary = impersonate(HANK, GARY);
		auth.impersonate(asGary, target(KEVIN));
		verify(tokenDao).setActAs("hash-" + HANK, KEVIN, HANK, NOW);
		auth.users(asGary);
		verify(userDao).findActiveWithRoles(HANK);
	}

	@Test
	void 切回_未切換中409E20_切換中寫IMPERSONATE_END() {
		assertStatus(() -> auth.endImpersonate(id(HANK)), HttpStatus.CONFLICT, AcctMsg.E20);
		MeInfo me = auth.endImpersonate(impersonate(HANK, GARY));
		verify(tokenDao).setActAs(eq("hash-" + HANK), isNull(), eq(HANK), eq(NOW));
		verify(adminLogDao).insert(eq(NOW), eq(HANK), any(), eq(AcctConst.ADM_IMPERSONATE_END), eq(GARY), any(),
				eq(AcctConst.ADM_SUCCESS));
		assertThat(me.getUserId()).isEqualTo(HANK);
		assertThat(me.getCanImpersonate()).isTrue();
	}

	@Test
	void 名單_無權限403() {
		assertStatus(() -> auth.users(id(KEVIN)), HttpStatus.FORBIDDEN, AcctMsg.P01);
	}

	@Test
	void 登出_寫LOGOUT_DATE() {
		auth.logout(id(KEVIN));
		verify(tokenDao).logout("hash-" + KEVIN, KEVIN, NOW);
	}

	// ------------------------------------------------------------

	static AcctUser user(String id, int status) {
		AcctUser u = new AcctUser();
		u.setUserId(id);
		String full = TestFixture.NAMES.get(id);
		u.setUserEnName(full.substring(0, full.indexOf(' ')));
		u.setUserName(full.substring(full.indexOf('(') + 1, full.length() - 1));
		u.setEmail(id + "@example.com");
		u.setTelNo("#" + id);
		u.setPwdHash("PBKDF2$h");
		u.setStatus(status);
		return u;
	}

	static LoginToken token(LocalDateTime lastAccess, LocalDateTime expire, LocalDateTime logout, int userStatus) {
		LoginToken t = new LoginToken();
		t.setUserId(KEVIN);
		t.setLastAccessDate(lastAccess);
		t.setExpireDate(expire);
		t.setLogoutDate(logout);
		t.setUserStatus(userStatus);
		return t;
	}

	static LoginForm login(String userId, String pwd) {
		LoginForm f = new LoginForm();
		f.setUserId(userId);
		f.setPassword(pwd);
		return f;
	}

	static ImpersonateForm target(String userId) {
		ImpersonateForm f = new ImpersonateForm();
		f.setTargetUserId(userId);
		return f;
	}
}
