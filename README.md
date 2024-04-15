# Customs Inventory Linking Exports

The Customs Inventory Linking Exports service provides an interface that allows users to submit requests for the consolidation, movement, or querying of consignments for inventory linking purposes.

The objective of the service POST API is as follows:

1. Receive a request from a user wishing to submit an inventory linking declaration
2. Validate the request payload conforms to the schema
3. Pass the request to the backend
4. Respond to the declarant indicating the success of steps 2 / 3.

It is assumed that the underlying backend process is asynchronous, and that the only response to the declarant from this API is to indicate the success (or otherwise) of the validation and submission to downstream system.

## Development Setup
- Run locally: `sbt run` which runs on port `9649` by default
- Run with test endpoints: `sbt 'run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes'`

##  Service Manager Profiles
The Customs Inventory Linking Exports service can be run locally from Service Manager, using the following profiles:


| Profile Details                       | Command                                                           | Description                                                    |
|---------------------------------------|:------------------------------------------------------------------|----------------------------------------------------------------|
| CUSTOMS_DECLARATION_ALL               | sm2 --start CUSTOMS_DECLARATION_ALL                               | To run all CDS applications.                                   |
| CUSTOMS_INVENTORY_LINKING_EXPORTS_ALL | sm2 --start CUSTOMS_INVENTORY_LINKING_EXPORTS_ALL                 | To run all CDS Inventory Linking Exports related applications. |
| CUSTOMS_INVENTORY_LINKING_IMPORTS_ALL | sm2 --start CUSTOMS_INVENTORY_LINKING_IMPORTS_ALL                 | To run all CDS Inventory Linking Imports related applications. |


## Run Tests
- Run Unit Tests: `sbt test`
- Run Integration Tests: `sbt IntegrationTest/test`
- Run Unit and Integration Tests: `sbt test IntegrationTest/test`
- Run Unit and Integration Tests with coverage report: `./run_all_tests.sh`<br/> which runs `sbt clean scalastyle coverage test it:test coverageReport"`

### Useful CURL commands for local testing
[link to curl commands](docs/curl-commands.md)

### Acceptance Tests
To run the CDS acceptance tests, see [here](https://github.com/hmrc/customs-automation-test).

### Performance Tests
To run performance tests, see [here](https://github.com/hmrc/customs-declaration-performance-test).


## API documentation
For Customs Inventory Linking Exports documentation, see [here](https://developer.service.hmrc.gov.uk/guides/customs-declarations-end-to-end-service-guide/documentation/inventory-linking-export-declarations.html#inventory-linking-for-export-declarations).


### API Notification Pull specific routes
| Path - internal routes prefixed by `/customs/inventory-linking/exports` | Supported Methods | Description                          |
|-------------------------------------------------------------------------|:-----------------:|--------------------------------------|
| `/`                                                                     |       POST        | Submit an Inventory Exports Request. |


### Test-only specific routes
This service does not have any specific test-only endpoints.

# Custom SBT Task for generating ZIP file containing schemas
There is an SBT task `zipXsds` that generates a ZIP file containing schemas, for each version under `/public/api/conf`. 
These ZIP files are referenced by the RAML. These references are rendered as HTML links to generated ZIP in the deployed service. 

# Lookup of `fieldsId` UUID and `authenticatedEori` from `api-subscription-fields` service
The `X-Client-ID` header, together with the application context and version are used
 to call the `api-subscription-fields` service to get the unique `fieldsId` UUID and to put this value in the `api-subscription-fields-id`
 header. Note if the user is a CSP and the X-Submitter-Identifier header is not supplied, then it is assumed that the 
 CSP is submitting a direct transaction that has originated with the CSP, and the `authenticatedEori` field is used instead.

So there is now a direct dependency on the `api-subscription-fields` service. Note the service to get the `fieldsId` is not currently stubbed. 

## Seeding Data in `api-subscription-fields` for local end to end testing

Make sure the `api-subscription-fields` service is running on port `9650`. Then run the below curl command.
 - Please note that the UUID `6372609a-f550-11e7-8c3f-9a214cf093ae` is used as an example - please generate your own.

    curl -v -X PUT "http://localhost:9650/field/application/6372609a-f550-11e7-8c3f-9a214cf093ae/context/customs%2Finventory-linking%2Fexports/version/1.0" -H "Cache-Control: no-cache" -H "Content-Type: application/json" -d '{ "fields" : { "callbackUrl" : "https://postman-echo.com/post", "securityToken" : "securityToken" } }'

When you then send a request to `customs-inventory-linking-exports` make sure you have the HTTP header `X-Client-ID` with the value `6372609a-f550-11e7-8c3f-9a214cf093ae`    


# Switching service endpoints

Dynamic switching of service endpoints has been implemented for mdg connector. To configure dynamic
switching of the endpoint there must be a corresponding section in the application config file
(see example below). This should contain the endpoint config details.


## Example
The service `mdg-exports` has a `default` configuration and a `stub` configuration. Note
that `default` configuration is declared directly inside the `customs-inventory-linking-exports` section.

        services {
          ...

          mdg-exports {
              host = localhost
              port = 9477
              bearer-token = "real"
              context = /inventorylinking/exportsinbound/1.0.0
            
              stub {
                host = localhost
                port = 9478
                bearer-token = "real"
                context = /inventorylinking/exportsinbound
              }
            }
        }
    
### Switch service configuration for an endpoint
`test-only` endpoints work if and only if `-Dapplication.router=testOnlyDoNotUseInAppConf.Routes` is provided

#### REQUEST
    curl -X "POST" http://localhost:9823/test-only/service/mdg-exports/configuration -H 'content-type: application/json' -d '{ "environment": "stub" }'

#### RESPONSE

    The service mdg-exports is now configured to use the stub environment

### Switch service configuration to default for an endpoint

#### REQUEST

    curl -X "POST" http://localhost:9823/test-only/service/mdg-exports/configuration -H 'content-type: application/json' -d '{ "environment": "default" }'

#### RESPONSE

    The service mdg-exports is now configured to use the default environment

### Get the current configuration for a service

#### REQUEST

    curl -X "GET" http://localhost:9823/test-only/service/mdg-exports/configuration

#### RESPONSE

    {
      "service": "mdg-exports",
      "environment": "stub",
      "url": "http://currenturl/customs-inventory-linking-exports"
      "bearerToken": "current token"
    }
