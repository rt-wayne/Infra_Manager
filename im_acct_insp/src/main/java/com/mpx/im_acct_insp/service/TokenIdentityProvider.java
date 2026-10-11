package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：第一階段 IdentityProvider 實作——本模組自建 token（spec v2.0 §2.1、§2.2、§2.5）
//           Bearer → SHA-256 → IM_ACCT_LOGIN_TOKEN；有效條件：未登出、現在 < EXPIRE_DATE（登入＋8 小時）、
//           現在 − LAST_ACCESS_DATE ≤ 30 分鐘、實際使用者 STATUS＝1；任一不符 401 A03。
//           通過後 LAST_ACCESS_DATE 節流為每 60 秒最多寫一次；代理身分 STATUS 變 0 時自動視為切回（清 ACT_AS_USER_ID，不寫軌跡）。
//           權限、角色、執行人類別每次請求自 DB 載入（不快取）。日後換共用登入只換此類別
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-11
// 變更說明: BACKLOG 第 28 項：閒置上限改 480 分鐘（AcctConst.TOKEN_IDLE_MINUTES，本類別程式未改）
// ============================================================

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.common.IdentityProvider;
import com.mpx.im_acct_insp.common.Texts;
import com.mpx.im_acct_insp.common.TokenCodec;
import com.mpx.im_acct_insp.dao.LoginTokenDao;
import com.mpx.im_acct_insp.dao.UserDao;
import com.mpx.im_acct_insp.model.AcctUser;
import com.mpx.im_acct_insp.model.LoginToken;

@Service
public class TokenIdentityProvider implements IdentityProvider {

	private final LoginTokenDao tokenDao;
	private final UserDao userDao;
	private final PermissionService permissionService;
	private final Clock clock;

	public TokenIdentityProvider(LoginTokenDao tokenDao, UserDao userDao, PermissionService permissionService, Clock clock) {
		this.tokenDao = tokenDao;
		this.userDao = userDao;
		this.permissionService = permissionService;
		this.clock = clock;
	}

	@Override
	public Identity resolve(String authorizationHeader) {
		String token = TokenCodec.parseBearer(authorizationHeader);
		if (token == null) {
			throw AcctMsg.unauthorized();
		}
		String hash = TokenCodec.sha256Hex(token);
		LoginToken t = tokenDao.find(hash);
		LocalDateTime now = LocalDateTime.now(clock);
		if (!isValid(t, now)) {
			throw AcctMsg.unauthorized();
		}
		if (needTouch(t.getLastAccessDate(), now)) {
			tokenDao.touch(hash, now);
		}
		String actAs = t.getActAsUserId();
		if (actAs != null && !Integer.valueOf(1).equals(t.getActAsStatus())) {
			// 代理身分已停用：自動切回，不寫軌跡（spec §2.5）
			tokenDao.setActAs(hash, null, t.getUserId(), now);
			actAs = null;
		}
		AcctUser real = userDao.findById(t.getUserId());
		if (real == null) {
			throw AcctMsg.unauthorized();
		}
		AcctUser eff = actAs == null ? real : userDao.findById(actAs);
		if (eff == null) {
			eff = real;
		}
		return build(hash, real, eff);
	}

	/** 依實際使用者與有效身分組出 Identity（權限自 DB 載入）；登入、切換身分後也用此方法 */
	public Identity build(String tokenHash, AcctUser real, AcctUser effective) {
		boolean impersonating = !real.getUserId().equals(effective.getUserId());
		PermissionService.Grants g = permissionService.load(effective.getUserId());
		return new Identity(tokenHash, real.getUserId(), displayName(real), effective.getUserId(), displayName(effective),
				impersonating, g.permissions(), g.roleIds(), g.execCatgIds(), effective.getEmail(), effective.getTelNo());
	}

	public static String displayName(AcctUser u) {
		return Texts.displayName(u.getUserEnName(), u.getUserName(), u.getUserId());
	}

	/** token 有效條件（spec §2.1） */
	static boolean isValid(LoginToken t, LocalDateTime now) {
		if (t == null || t.getLogoutDate() != null || t.getExpireDate() == null || t.getLastAccessDate() == null) {
			return false;
		}
		if (!now.isBefore(t.getExpireDate())) {
			return false;
		}
		if (Duration.between(t.getLastAccessDate(), now).compareTo(Duration.ofMinutes(AcctConst.TOKEN_IDLE_MINUTES)) > 0) {
			return false;
		}
		return Integer.valueOf(1).equals(t.getUserStatus());
	}

	static boolean needTouch(LocalDateTime lastAccess, LocalDateTime now) {
		return Duration.between(lastAccess, now).getSeconds() >= AcctConst.TOKEN_TOUCH_SECONDS;
	}
}
