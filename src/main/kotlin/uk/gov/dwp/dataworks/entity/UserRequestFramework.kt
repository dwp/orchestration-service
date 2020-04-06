package uk.gov.dwp.dataworks.entity

class UserRequestFramework {
    private lateinit var ecs_cluster_name: String
    private lateinit var user_name: String
    private lateinit var emr_cluster_host_name: String
    private lateinit var alb_name: String

    fun getEcs_cluster_name(): String{
        return ecs_cluster_name
    }
    fun setEcs_cluster_name(ecs_cluster_name: String){
        this.ecs_cluster_name = ecs_cluster_name
    }
    fun getUser_name(): String{
        return user_name
    }
    fun setUser_name(user_name: String){
        this.user_name = user_name
    }
    fun getEmr_cluster_host_name(): String{
        return emr_cluster_host_name
    }
    fun setEmr_cluster_host_name(emr_cluster_id: String){
        this.emr_cluster_host_name = emr_cluster_id
    }
    fun getalb_name(): String{
        return alb_name
    }
    fun setalb_name(alb_name: String){
        this.alb_name = alb_name
    }
}