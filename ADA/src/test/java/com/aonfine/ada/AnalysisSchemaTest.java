package com.aonfine.ada;

import static org.junit.Assert.*;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.Test;
import com.aonfine.ada.analysis.AnalysisState;

public class AnalysisSchemaTest {
    @Test public void additiveMigrationPreservesRowsAndEnforcesIdentityRelationships() throws Exception {
        DataSource ds = LocalDatabase.create();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            insertDump(s, "dump1", "FAILED"); insertDump(s, "dump2", "STORED");
        }
        LocalDatabase.run(ds, "sql/ada-migration-20260909-analysis-jobs.sql");
        LocalDatabase.run(ds, "sql/ada-analysis-tables-cubrid.sql");
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM TB_ADA_DUMP d JOIN TB_ADA_DUMP_BAK_20260909_JOBS b "
                    + "ON d.DUMP_ID=b.DUMP_ID AND d.STATUS_CD=b.STATUS_CD AND d.HOST_NM=b.HOST_NM AND d.FAIL_REASON=b.FAIL_REASON")) {
                r.next(); assertEquals(2, r.getInt(1));
            }
            s.execute("INSERT INTO TB_ADA_ANALYSIS_JOB(JOB_ID,DUMP_ID,REQUESTED_BY) VALUES('job1','dump1','alice')");
            s.execute("INSERT INTO TB_ADA_ANALYSIS_JOB(JOB_ID,DUMP_ID,REQUESTED_BY) VALUES('job2','dump2','alice')");
            rejected(s, "INSERT INTO TB_ADA_ANALYSIS_JOB(JOB_ID,DUMP_ID,REQUESTED_BY) VALUES('duplicate','dump1','alice')");
            s.execute("INSERT INTO TB_ADA_ANALYSIS_ATTEMPT(JOB_ID,ATTEMPT_ID,ATTEMPT_NO,REQUEST_KEY,REQUESTED_BY) VALUES('job1','attempt1',1,'request1','alice')");
            rejected(s, "UPDATE TB_ADA_ANALYSIS_JOB SET CURRENT_ATTEMPT_ID='attempt1' WHERE JOB_ID='job2'");
            rejected(s, "INSERT INTO TB_ADA_ANALYSIS_ATTEMPT(JOB_ID,ATTEMPT_ID,ATTEMPT_NO,REQUEST_KEY,REQUESTED_BY) VALUES('job1','duplicate',1,'request2','alice')");
            rejected(s, "INSERT INTO TB_ADA_ANALYSIS_ATTEMPT(JOB_ID,ATTEMPT_ID,ATTEMPT_NO,REQUEST_KEY,REQUESTED_BY) VALUES('job1','duplicate',2,'request1','alice')");
            s.execute("UPDATE TB_ADA_ANALYSIS_JOB SET CURRENT_ATTEMPT_ID='attempt1',STATUS_CD='QUEUED',VERSION_NO=1 WHERE JOB_ID='job1'");
            assertEquals(1, s.executeUpdate("UPDATE TB_ADA_ANALYSIS_JOB SET STATUS_CD='RUNNING',VERSION_NO=2 WHERE JOB_ID='job1' AND CURRENT_ATTEMPT_ID='attempt1' AND STATUS_CD='QUEUED' AND VERSION_NO=1"));
            assertEquals(0, s.executeUpdate("UPDATE TB_ADA_ANALYSIS_JOB SET STATUS_CD='RUNNING',VERSION_NO=2 WHERE JOB_ID='job1' AND CURRENT_ATTEMPT_ID='attempt1' AND STATUS_CD='QUEUED' AND VERSION_NO=1"));
            s.execute("INSERT INTO TB_ADA_ANALYSIS_ATTEMPT(JOB_ID,ATTEMPT_ID,ATTEMPT_NO,REQUEST_KEY,REQUESTED_BY) VALUES('job1','attempt2',2,'request2','alice')");
            s.execute("UPDATE TB_ADA_ANALYSIS_JOB SET CURRENT_ATTEMPT_ID='attempt2',VERSION_NO=3 WHERE JOB_ID='job1'");
            assertEquals(0, s.executeUpdate("UPDATE TB_ADA_ANALYSIS_JOB SET STATUS_CD='SUCCEEDED' WHERE JOB_ID='job1' AND CURRENT_ATTEMPT_ID='attempt1' AND VERSION_NO=2"));
            try { LocalDatabase.run(ds, "sql/ada-migration-20260909-analysis-jobs.sql"); fail("rerun must stop rather than replace backup"); }
            catch (SQLException expected) { }
        }
    }
    @Test public void freshInstallCreatesEmptyAnalysisTables() throws Exception {
        DataSource ds = LocalDatabase.create(); LocalDatabase.run(ds, "sql/ada-analysis-tables-cubrid.sql");
        try (Connection c = ds.getConnection(); ResultSet r = c.createStatement().executeQuery("SELECT COUNT(*) FROM TB_ADA_ANALYSIS_JOB")) {
            r.next(); assertEquals(0, r.getInt(1));
        }
    }
    @Test public void staleReportsAndUnconfirmedRecoveryCannotStartNewAttempt() {
        assertFalse(AnalysisState.acceptsReport("new", "old", 4, 4, AnalysisState.RUNNING, AnalysisState.SUCCEEDED));
        assertFalse(AnalysisState.acceptsReport("new", "new", 4, 3, AnalysisState.RUNNING, AnalysisState.SUCCEEDED));
        assertFalse(AnalysisState.acceptsReport("new", "new", 4, 4, AnalysisState.CANCEL_REQUESTED, AnalysisState.SUCCEEDED));
        assertTrue(AnalysisState.acceptsReport("new", "new", 4, 4, AnalysisState.CANCEL_REQUESTED, AnalysisState.CANCELLED));
        assertFalse(AnalysisState.RECOVERY_REQUIRED.mayCreateAttempt());
        assertFalse(AnalysisState.RUNNING.mayCreateAttempt()); assertFalse(AnalysisState.CANCEL_REQUESTED.mayCreateAttempt());
        assertTrue(AnalysisState.FAILED.mayCreateAttempt()); assertTrue(AnalysisState.CANCELLED.mayCreateAttempt());
    }
    private void insertDump(Statement s, String id, String status) throws SQLException {
        s.execute("INSERT INTO TB_ADA_DUMP(DUMP_ID,USER_ID,DUMP_TYPE,DEPT_NM,TASK_NM,HOST_NM,ORIGINAL_FILE_NM,STORED_FILE_NM,FILE_SIZE,STATUS_CD,FAIL_REASON) "
                + "VALUES('" + id + "','alice','THREAD','dept','task','host','file.tar.gz','" + id + ".tar.gz',12,'" + status + "','preserve reason')");
    }
    private void rejected(Statement s, String sql) throws SQLException {
        try { s.execute(sql); fail("expected constraint rejection"); } catch (SQLException expected) { }
    }
}
