# orchestration-service
The service orchestrator for providing remote access into the analytical environment

###Endpoint ot submit request fo user containers
 To be submitted as a post request to `/fesreq`  
 
 Body of request must contain the following fields:
  - `ecs_cluster_name`
  - `user_name`
  - `emr_cluster_host_name`
 


![Image of Orchestration Service](OrchestrationService.png)
