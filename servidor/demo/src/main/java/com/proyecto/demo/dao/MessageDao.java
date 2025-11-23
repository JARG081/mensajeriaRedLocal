package com.proyecto.demo.dao;

import com.proyecto.demo.model.MessageRecord;
import java.util.List;

public interface MessageDao {
    long insertMessage(MessageRecord m);
    List<MessageRecord> findBetweenUsers(long userAId, long userBId, int limit);
    List<MessageRecord> findForUser(long userId, int limit);
    void ensureTables();
}
