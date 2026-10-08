package com.dwl.mutcube.core.ai

fun ProviderConfiguration.resolveProjectProfile(profileId: String?): ProviderProfile {
    val profile = if (profileId == null) activeProfile else {
        profiles.firstOrNull { it.id == profileId }
            ?: throw IllegalArgumentException("项目绑定的 Provider 已删除，请在项目设置中重新选择")
    }
    require(profile.enabled && profile.modelId.isNotBlank()) { "${profile.name} 已停用或未配置模型，请在设置中检查" }
    return profile
}
