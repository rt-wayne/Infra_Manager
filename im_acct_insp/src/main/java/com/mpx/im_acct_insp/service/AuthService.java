package com.mpx.im_acct_insp.service;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-10
// 變更說明: 新增：登入、登出、/me、切換身分名單、切換身分／切回（spec v2.0 §2.1、§2.5、§6.2）
//           登入：工號不存在、停用、無 PWD_HASH → 仍做一次 dummyHash 後回 401 A01（回應時間不洩漏帳號是否存在）；
//           LOCK_DATE 非空 → 401 A02（即使密碼正確）；密碼錯 → LOGIN_FAIL_CNT＋1、達 5 次同句寫 LOCK_DATE，回 A01；
//           成功 → 歸 0、寫 LAST_LOGIN_DATE、發新 token（DB 只存 SHA-256、EXPIRE_DATE＝現在＋8 小時；允許多個 token 並存）。
//           切換身分：實際使用者需 SYS_IMPERSONATE（無權限 403 並寫 ADMIN_LOG DENY）；對象須 STATUS＝1 且不是自己；
//           UPDATE token 列與 INSERT 管理軌跡同一交易（spec §5）；切回未切換中 409 E20。
//           log 不記密碼、token、雜湊值
//           修改：登入成功或失敗計數的 UPDATE 影響 0 筆（查詢後才被鎖定）→ 401 A02、不發 token，
//           鎖定後不論密碼對錯一律 A02（避免並發猜密碼時以 A01／A02 判斷密碼是否正確）
// ============================================================

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mpx.im_acct_insp.common.AcctConst;
import com.mpx.im_acct_insp.common.AcctMsg;
import com.mpx.im_acct_insp.common.ApiException;
import com.mpx.im_acct_insp.common.Identity;
import com.mpx.im_acct_insp.common.PasswordHasher;
import com.mpx.im_acct_insp.common.Texts;
import com.mpx.im_acct_insp.common.TokenCodec;
import com.mpx.im_acct_insp.dao.AdminLogDao;
import com.mpx.im_acct_insp.dao.LoginTokenDao;
import com.mpx.im_acct_insp.dao.UserDao;
import com.mpx.im_acct_insp.model.AcctUser;
import com.mpx.im_acct_insp.model.LoginResult;
import com.mpx.im_acct_insp.model.MeInfo;
import com.mpx.im_acct_insp.model.UserInfo;
import com.mpx.im_acct_insp.model.UserRoleRow;
import com.mpx.im_acct_insp.model.request.ImpersonateForm;
import com.mpx.im_acct_insp.model.request.LoginForm;

import tools.jackson.databind.json.JsonMapper;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();
	/** TARGET_USER_ID 欄寬 */
	static final int USER_ID_MAX = 30;

	private final UserDao userDao;
	private final LoginTokenDao tokenDao;
	private final AdminLogDao adminLogDao;
	private final PasswordHasher passwordHasher;
	private final TokenCodec tokenCodec;
	private final TokenIdentityProvider identityProvider;
	private final PermissionService permissionService;
	private final TransactionTemplate tx;
	private final Clock clock;

	public AuthService(UserDao userDao, LoginTokenDao tokenDao, AdminLogDao adminLogDao, PasswordHasher passwordHasher,
			TokenCodec tokenCodec, TokenIdentityProvider identityProvider, PermissionService permissionService,
			TransactionTemplate tx, Clock clock) {
		this.userDao = userDao;
		this.tokenDao = tokenDao;
		this.adminLogDao = adminLogDao;
		this.passwordHasher = passwordHasher;
		this.tokenCodec = tokenCodec;
		this.identityProvider = identityProvider;
		this.permissionService = permissionService;
		this.tx = tx;
		this.clock = clock;
	}

	// ------------------------------------------------------------
	// 登入／登出
	// ------------------------------------------------------------

	public LoginResult login(LoginForm form) {
		String userId = form == null ? null : Texts.trimToNull(form.getUserId());
		char[] pwd = form == null || form.getPassword() == null ? new char[0] : form.getPassword().toCharArray();
		try {
			AcctUser u = userId == null || userId.length() > USER_ID_MAX ? null : userDao.findById(userId);
			if (u == null || !Integer.valueOf(1).equals(u.getStatus()) || u.getPwdHash() == null) {
				passwordHasher.dummyHash(pwd);
				throw ApiException.unauthorized(AcctMsg.A01);
			}
			if (u.getLockDate() != null) {
				passwordHasher.dummyHash(pwd);
				throw ApiException.unauthorized(AcctMsg.A02);
			}
			LocalDateTime now = LocalDateTime.now(clock);
			if (!passwordHasher.verify(pwd, u.getPwdHash())) {
				// 查詢後才被鎖定 → 影響 0 筆，回 A02；與密碼正確時同代號，鎖定後回應不洩漏密碼對錯
				if (userDao.loginFail(userId, AcctConst.LOGIN_MAX_FAIL, now) != 1) {
					log.info("登入被拒（密碼錯誤，驗證期間帳號已鎖定）userId={}", userId);
					throw ApiException.unauthorized(AcctMsg.A02);
				}
				log.info("登入失敗（密碼錯誤）userId={}", userId);
				throw ApiException.unauthorized(AcctMsg.A01);
			}
			// 查詢後到此之間被其他請求鎖定（LOCK_DATE 已寫入）→ 影響 0 筆，不發 token
			if (userDao.loginOk(userId, now) != 1) {
				// log 註明密碼正確：回應與密碼錯時相同，但內部要能追查鎖定瞬間是否有人猜中
				log.info("登入被拒（密碼正確，驗證期間帳號已鎖定）userId={}", userId);
				throw ApiException.unauthorized(AcctMsg.A02);
			}
			String token = tokenCodec.newToken();
			String hash = TokenCodec.sha256Hex(token);
			tokenDao.insert(hash, userId, now.plusHours(AcctConst.TOKEN_MAX_HOURS), now);
			log.info("登入成功 userId={}", userId);
			LoginResult result = new LoginResult();
			result.setToken(token);
			result.setMe(me(identityProvider.build(hash, u, u)));
			return result;
		} finally {
			Arrays.fill(pwd, '\0');
		}
	}

	public void logout(Identity id) {
		tokenDao.logout(id.getTokenHash(), id.getRealUserId(), LocalDateTime.now(clock));
		log.info("登出 userId={}", id.getRealUserId());
	}

	// ------------------------------------------------------------
	// /me、/users
	// ------------------------------------------------------------

	public MeInfo me(Identity id) {
		MeInfo me = new MeInfo();
		me.setUserId(id.getUserId());
		me.setUserName(id.getUserName());
		me.setRealUserId(id.getRealUserId());
		me.setRealUserName(id.getRealUserName());
		me.setImpersonating(id.isImpersonating());
		me.setCanImpersonate(!id.isImpersonating() && permissionService.can(id, AcctConst.PERM_SYS_IMPERSONATE));
		me.setPermissions(id.getPermissions().stream().sorted().toList());
		me.setRoleIds(id.getRoleIds());
		me.setExecCatgIds(id.getExecCatgIds());
		me.setTelNo(id.getTelNo());
		me.setEmail(id.getEmail());
		return me;
	}

	/** 切換身分名單：STATUS＝1、不含實際使用者自己 */
	public List<UserInfo> users(Identity id) {
		if (!permissionService.realCan(id, AcctConst.PERM_SYS_IMPERSONATE)) {
			throw AcctMsg.forbidden();
		}
		Map<String, UserInfo> users = new LinkedHashMap<>();
		for (UserRoleRow row : userDao.findActiveWithRoles(id.getRealUserId())) {
			UserInfo u = users.computeIfAbsent(row.getUserId(), k -> {
				UserInfo n = new UserInfo();
				n.setUserId(row.getUserId());
				n.setUserName(Texts.displayName(row.getUserEnName(), row.getUserName(), row.getUserId()));
				n.setRoleIds(new ArrayList<>());
				return n;
			});
			if (row.getRoleId() != null && !u.getRoleIds().contains(row.getRoleId())) {
				u.getRoleIds().add(row.getRoleId());
			}
		}
		return new ArrayList<>(users.values());
	}

	// ------------------------------------------------------------
	// 切換身分（spec §2.5）
	// ------------------------------------------------------------

	public MeInfo impersonate(Identity id, ImpersonateForm form) {
		String target = form == null ? null : Texts.trimToNull(form.getTargetUserId());
		LocalDateTime now = LocalDateTime.now(clock);
		if (!permissionService.realCan(id, AcctConst.PERM_SYS_IMPERSONATE)) {
			adminLogDao.insert(now, id.getRealUserId(), id.getRealUserName(), AcctConst.ADM_IMPERSONATE,
					target != null && target.length() <= USER_ID_MAX ? target : null, null, AcctConst.ADM_DENY);
			log.info("切換身分被拒（無權限）userId={}", id.getRealUserId());
			throw AcctMsg.forbidden();
		}
		if (target == null) {
			throw AcctMsg.required("切換對象");
		}
		if (target.equals(id.getRealUserId())) {
			throw ApiException.badRequest(AcctMsg.IMPERSONATE_SELF);
		}
		AcctUser t = target.length() > USER_ID_MAX ? null : userDao.findById(target);
		if (t == null || !Integer.valueOf(1).equals(t.getStatus())) {
			throw AcctMsg.notFound();
		}
		String targetName = TokenIdentityProvider.displayName(t);
		tx.executeWithoutResult(s -> {
			if (tokenDao.setActAs(id.getTokenHash(), target, id.getRealUserId(), now) != 1) {
				throw AcctMsg.unauthorized();
			}
			adminLogDao.insert(now, id.getRealUserId(), id.getRealUserName(), AcctConst.ADM_IMPERSONATE, target,
					contentJson(targetName), AcctConst.ADM_SUCCESS);
		});
		log.info("切換身分 userId={} target={}", id.getRealUserId(), target);
		AcctUser real = userDao.findById(id.getRealUserId());
		return me(identityProvider.build(id.getTokenHash(), real, t));
	}

	public MeInfo endImpersonate(Identity id) {
		if (!id.isImpersonating()) {
			throw ApiException.conflict(AcctMsg.E20);
		}
		LocalDateTime now = LocalDateTime.now(clock);
		String prev = id.getUserId();
		String prevName = id.getUserName();
		tx.executeWithoutResult(s -> {
			if (tokenDao.setActAs(id.getTokenHash(), null, id.getRealUserId(), now) != 1) {
				throw AcctMsg.unauthorized();
			}
			adminLogDao.insert(now, id.getRealUserId(), id.getRealUserName(), AcctConst.ADM_IMPERSONATE_END, prev,
					contentJson(prevName), AcctConst.ADM_SUCCESS);
		});
		log.info("切回 userId={} from={}", id.getRealUserId(), prev);
		AcctUser real = userDao.findById(id.getRealUserId());
		return me(identityProvider.build(id.getTokenHash(), real, real));
	}

	/** CONTENT_JSON：切換時記對象顯示名 */
	static String contentJson(String targetUserName) {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("targetUserName", targetUserName);
		return JSON.writeValueAsString(m);
	}
}
