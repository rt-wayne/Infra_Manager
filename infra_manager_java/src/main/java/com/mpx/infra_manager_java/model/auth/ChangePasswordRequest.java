package com.mpx.infra_manager_java.model.auth;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：POST /api/auth/password 請求本文（S2 回合二）。
//           這裡只擋「本文格式」（缺欄位、空字串、舊密碼超過 128 字）→ 400「請求格式錯誤」；
//           密碼規則（不得全空白、至少 6 字、UTF-8 ≤ 72 bytes、不得等於預設密碼或舊密碼、舊密碼要對）在 AuthService 檢查，
//           各自有明確訊息讓使用者知道怎麼改。新密碼的長度上限不在這裡擋：超長一律由 AuthService 回「新密碼過長」，
//           不然超過 128 字會變成「請求格式錯誤」，使用者看不出該怎麼改（code review 第 7 項）。
// ============================================================

public record ChangePasswordRequest(String oldPassword, String newPassword) {

	public boolean isValid() {
		return oldPassword != null && !oldPassword.isEmpty() && oldPassword.length() <= LoginRequest.PASSWORD_MAX
				&& newPassword != null && !newPassword.isEmpty();
	}
}
