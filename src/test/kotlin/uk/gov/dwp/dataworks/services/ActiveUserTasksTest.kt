package uk.gov.dwp.dataworks.services

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import uk.gov.dwp.dataworks.UserHasNoTasksException

@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [ActiveUserTasks::class])
class ActiveUserTasksTest {
    @Autowired
    private lateinit var activeUserTasks: ActiveUserTasks

    @Test
    fun `Exception thrown when getting a value not in map`() {
        val userName = "non-existent"
        assertThatCode { activeUserTasks.get(userName) }
                .hasMessage("No tasks found for $userName")
                .isInstanceOf(UserHasNoTasksException::class.java)
    }
}
