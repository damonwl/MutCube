package com.dwl.mutcube.template.builtin

import com.dwl.mutcube.template.core.TemplateManifest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FitnessManifestExportTest {
    @Test fun exportsCurrentContractWhenRequested() {
        val json = FitnessTemplate.manifestJson()
        assertEquals(FitnessTemplate.manifest(), TemplateManifest.parse(json))
        System.getProperty("fitnessExportPath")?.let { path ->
            File(path).apply { parentFile?.mkdirs() }.writeText(json)
        }
    }
}
