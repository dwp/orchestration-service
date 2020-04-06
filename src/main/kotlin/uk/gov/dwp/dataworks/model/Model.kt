package uk.gov.dwp.dataworks.model

import com.fasterxml.jackson.annotation.JsonCreator

data class Model @JsonCreator constructor(
        val ecsClusterName: String,
        val userName: String,
        val emrClusterHostName: String,
        val albName: String,
        val containerPort: Int,
        val jupyterCpu: Int,
        val jupyterMemory: Int
)
