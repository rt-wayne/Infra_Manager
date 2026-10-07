package com.mpx.infra_manager_java.service.changerequest;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-07
// 變更說明: 新增：送審必填檢核的單元測試（S7 R1，施工計畫 ②A）。鎖定：四個固定必填、至少一個執行人員、
//           REMOTE 才要連線方式、勾廠商才要廠商三欄、設備位置不檢核、缺漏一次全列並以「、」串接
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.mpx.infra_manager_java.model.changerequest.AppRow;
import com.mpx.infra_manager_java.web.ApiBadRequestException;

class AppSubmitValidatorTest {

	private static AppRow complete() {
		AppRow a = new AppRow();
		a.setApplyDeptName("資訊處");
		a.setApplyTel("1234");
		a.setApplyEmail("it@example.com");
		a.setIsSelfExec(1);
		a.setIsSupExec(0);
		a.setWorkModeCode("ONSITE");
		a.setWorkSubj("主旨");
		return a;
	}

	@Test
	void 齊全的現場作業單通過_設備位置不檢核() {
		AppRow a = complete();
		a.setAreaName(null);
		a.setRackName(null);
		assertThat(AppSubmitValidator.missing(a)).isEmpty();
		AppSubmitValidator.validate(a);
	}

	@Test
	void 全部缺漏時一次列出並依畫面順序() {
		AppRow a = new AppRow();
		a.setIsSupExec(1);
		a.setWorkModeCode("REMOTE");
		assertThat(AppSubmitValidator.missing(a)).containsExactly("申請單位", "聯絡電話", "Email", "連線方式", "執行廠商",
				"廠商聯絡人", "廠商電話", "作業主題");
		assertThatThrownBy(() -> AppSubmitValidator.validate(a)).isInstanceOf(ApiBadRequestException.class)
				.hasMessage("送審前請先補齊：申請單位、聯絡電話、Email、連線方式、執行廠商、廠商聯絡人、廠商電話、作業主題");
	}

	@Test
	void 兩個執行人員都沒勾視為缺執行人員() {
		AppRow a = complete();
		a.setIsSelfExec(0);
		a.setIsSupExec(null);
		assertThat(AppSubmitValidator.missing(a)).containsExactly("執行人員");
	}

	@Test
	void 遠端作業才要連線方式_現場作業不要() {
		AppRow a = complete();
		a.setWorkModeCode("REMOTE");
		a.setRemoteMethod("  ");
		assertThat(AppSubmitValidator.missing(a)).containsExactly("連線方式");
		a.setRemoteMethod("VPN");
		assertThat(AppSubmitValidator.missing(a)).isEmpty();
		a.setWorkModeCode("ONSITE");
		a.setRemoteMethod(null);
		assertThat(AppSubmitValidator.missing(a)).isEmpty();
	}

	@Test
	void 勾委外廠商才要廠商三欄() {
		AppRow a = complete();
		a.setIsSelfExec(0);
		a.setIsSupExec(1);
		assertThat(AppSubmitValidator.missing(a)).containsExactly("執行廠商", "廠商聯絡人", "廠商電話");
		a.setSupName("廠商甲");
		a.setSupCntct("聯絡人");
		a.setSupTel("02-1234");
		assertThat(AppSubmitValidator.missing(a)).isEmpty();
	}
}
