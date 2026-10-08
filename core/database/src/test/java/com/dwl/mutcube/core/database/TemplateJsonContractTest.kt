package com.dwl.mutcube.core.database

import com.dwl.mutcube.template.core.TemplateJsonContract
import org.junit.Test

class TemplateJsonContractTest {
    private val schema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false}"""

    @Test
    fun acceptsDeclaredData() = TemplateJsonContract.validate(schema, """{"text":"hello"}""")

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUndeclaredData() = TemplateJsonContract.validate(schema, """{"text":"hello","secret":"no"}""")

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongType() = TemplateJsonContract.validate(schema, """{"text":123}""")
}
