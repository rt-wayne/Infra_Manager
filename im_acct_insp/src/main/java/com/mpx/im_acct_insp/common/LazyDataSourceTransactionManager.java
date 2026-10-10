package com.mpx.im_acct_insp.common;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-07
// 變更說明: 新增：延遲建立的交易管理器（spec v2.0 §5 取得方式）
//           common.db 的連線池在第一次取用時才向連線資訊 API 建立，不可在啟動時取 DataSource；
//           第一次開交易時才以 supplier（DbConnectionManager.getJdbcTemplate(別名).getJdbcTemplate().getDataSource()）
//           取得 DataSource 建 DataSourceTransactionManager，之後沿用（與 DbClient 同一個 Hikari 池，DbClient 的寫入會加入交易）。
//           取得失敗不快取，下次重試（與 DbConnectionManager 行為一致）
// ============================================================

import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

public class LazyDataSourceTransactionManager implements PlatformTransactionManager {

	private final Supplier<DataSource> dataSourceSupplier;
	private volatile DataSourceTransactionManager delegate;

	public LazyDataSourceTransactionManager(Supplier<DataSource> dataSourceSupplier) {
		this.dataSourceSupplier = dataSourceSupplier;
	}

	private DataSourceTransactionManager delegate() {
		DataSourceTransactionManager d = delegate;
		if (d == null) {
			synchronized (this) {
				d = delegate;
				if (d == null) {
					d = new DataSourceTransactionManager(dataSourceSupplier.get());
					delegate = d;
				}
			}
		}
		return d;
	}

	@Override
	public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
		return delegate().getTransaction(definition);
	}

	@Override
	public void commit(TransactionStatus status) throws TransactionException {
		delegate().commit(status);
	}

	@Override
	public void rollback(TransactionStatus status) throws TransactionException {
		delegate().rollback(status);
	}
}
