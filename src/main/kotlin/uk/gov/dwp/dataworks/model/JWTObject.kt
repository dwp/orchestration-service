package uk.gov.dwp.dataworks.model

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import uk.gov.dwp.dataworks.SystemArgumentException

data class JWTObject(val verifiedJWT: DecodedJWT, val userName: String) {
    val userUrl = ".../${userName}"
}