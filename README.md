# orchestration-service
The service orchestrator for providing remote access into the analytical environment

###Endpoint ot submit request fo user containers
 To be submitted as a post request to `/deployusercontainers`  
 
 Body of request must contain the following fields:
  - `ecsClusterName`
  - `userName`
  - `emrClusterHostName`
  - `albName`
      
  Optional inputs are:
  - `containerPorts`        - default : 443
  - `jupyterCpu`            - default : 512
  - `jupyterMemory `        - default : 512
  - `listOfAdditionalPermissions`* - default : null
  
     \* `listOfAdditionalPermissions` should be an array eg. `["s3:List*", "s3:Get*"]`

![Image of Orchestration Service](OrchestrationService.png)
