package com.graqr


import groovy.sql.Sql
import io.micronaut.configuration.picocli.PicocliRunner
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.context.env.Environment
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
@Requires(property = "test.datasources.default.url")
class PdpSpec extends Specification {

    @Shared
    @Value('${test.datasources.default.url}')
    String url

    @Shared
    Sql sql

    @Shared
    Random random

    @Shared
    String[] tcins

    @Shared
    String[][] locations //location_id, location_name

    @Shared
    final PrintStream originalOut = System.out
    @Shared
    final PrintStream originalErr = System.err

    @Shared
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
    ByteArrayOutputStream errStream = new ByteArrayOutputStream()

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)

    void setupSpec() {
        url = null == url ? System.getenv("TEST_DATASOURCES_DEFAULT_URL") : url
        try {
            sql = Sql.newInstance(url)
        }catch (NullPointerException e){
            throw new RuntimeException("Environment variable 'TEST_DATASOURCES_DEFAULT_URL' must be set", e)
        }
        tcins = sql.rows("select tcin FROM target_pdp TABLESAMPLE BERNOULLI (5) LIMIT 20")
                .collect(row -> row.tcin as String)
        locations = sql.rows('select location_id, location_name FROM target_stores TABLESAMPLE BERNOULLI (5) LIMIT 20')
                .collect(row -> new String[]{row.location_id as String, row.location_name as String})
        random = new Random()
    }

    def setup() {
        outputStream.reset()
        errStream.reset()
        System.setOut(new PrintStream(outputStream))
        System.setErr(new PrintStream(errStream))
    }

    def cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    def "tcin arg exceeding 1 count fails"() {
        when: "performing cli call 'pdp --tcin #tcin --store-id #locationId'"
        PicocliRunner.run(Pdp, ctx, '--tcin', tcin, "--store-id", locationId)

        then: "returns expected error message"
        def matcher = errStream.toString() =~ ".*only one tcin can be provided.*"
        matcher.size() == 1


        where:
        locationId                | tcin            | _
        locations[0][0] as String | tcins.join(",") | _
    }


    def "querying #tcin for #locationId returns expected data"() {
        when: "performing cli call 'pdp --tcin #tcin --store-id #locationId'"
        PicocliRunner.run(Pdp, ctx, "--tcin", tcin, "--store-id", locationId)

        then: "error stream is empty"
        errStream.toString().isBlank()

        and: "output contains data for #tcin"
        outputStream.toString().startsWith("{\"__typename\":\"Product\",\"tcin\":\"${tcin}\"")

        where:
        locationId                | tcin               | _
        locations[0][0] as String | tcins[0] as String | _
    }
}