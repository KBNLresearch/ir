package nl.kb.dare.model.preproces;

import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlBatch;

import java.util.List;

public interface RecordDao {


    @SqlBatch("INSERT INTO DARE_PREPROCES (ID, STATE, KBOBJID, TS_CREATE, FINGERPRINT, REPOSITORY_ID) " +
            "VALUES (SEQ_DARE_PREPROCES.nextval, :state, :kbObjId, CURRENT_TIMESTAMP, :fingerprint, :repositoryId)")
    void insertBatch(@BindBean List<Record> recordList);
}