package com.x8ing.mtl.server.mtlserver.db.repository.logs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@JsonPropertyOrder({
        "systemLogsRepository"
})
public class SystemLogService {


    private final SystemLogsRepository systemLogsRepository;

    public SystemLogService(SystemLogsRepository systemLogsRepository) {
        this.systemLogsRepository = systemLogsRepository;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(SystemLog.TOPIC1 topic1, String topic2, String topic3, String message, String detail) {
        SystemLog systemLog = new SystemLog();
        systemLog.setTopic1(topic1);
        systemLog.setTopic2(topic2);
        systemLog.setTopic3(topic3);
        systemLog.setCreateDate(new Date());
        systemLog.setMessage(message);
        systemLog.setDetail(detail);

        saveLog(systemLog);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(SystemLog systemLog) {
        systemLogsRepository.save(systemLog);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLogForException(Class<?> callingClass, String topic3, String message, Throwable t) {

        saveLog(
                SystemLog.TOPIC1.EXCEPTION,
                callingClass != null ? callingClass.getName() : "null",
                topic3,
                message,
                (t != null ? t.toString() : "null") + "\n" + ExceptionUtils.getStackTrace(t)
        );
    }

}
