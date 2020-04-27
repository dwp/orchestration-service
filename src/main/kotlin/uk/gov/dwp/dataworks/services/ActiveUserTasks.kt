package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.UserHasNoTasksException
import uk.gov.dwp.dataworks.UserTask
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Component
class ActiveUserTasks {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(ActiveUserTasks::class.java))
    }

    private val userTasks = mutableMapOf<String, UserTask>()

    fun put(userName: String, userTask: UserTask) {
        userTasks[userName] = userTask
        logger.info("User tasks registered", "correlation_id" to userTask.correlationId, "user_name" to userName)
    }

    fun get(userName: String): UserTask {
        return userTasks.getOrElse(userName) { throw UserHasNoTasksException("No tasks found for $userName") }
    }

    fun contains(userName: String): Boolean {
        return userTasks.contains(userName)
    }

    fun remove(userName: String) {
        val userTask = userTasks.remove(userName) ?: return
        logger.info("User tasks deregistered","correlation_id" to userTask.correlationId, "user_name" to userName)
    }
}
