package sample

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class HelloSpec : StringSpec({
    "can say hello to" {
        sayHelloTo("Kotlin") shouldBe "Hello Kotlin"
    }
})
