# Inventory Linking Exports Curl Commands
---
### Endpoints Summary

| Path                                          | Method | Description                                |
|-----------------------------------------------|--------|--------------------------------------------|
| [`/`](#user-content-post-export-payload)      | `POST` | Allows submission of an inventory linking export payload |

--- 
 
### POST export payload 
#### `POST /`
Allows submission of an inventory linking export payload
 
##### curl command
```
curl -v -X POST "http://localhost:9823/" \
  -H 'Accept: application/vnd.hmrc.1.0+xml' \
  -H 'Authorization: Bearer {TOKEN}' \
  -H 'Content-Type: application/xml;charset=utf-8' \
  -H 'X-Badge-Identifier: {Badge Id}' \
  -H 'X-Client-ID: {Client Id}' \
  -H 'X-EORI-Identifier: {EORI}' \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<inv:inventoryLinkingQueryRequest xmlns:inv="http://gov.uk/customs/inventoryLinking/v1">
    <inv:queryUCR>
        <inv:ucr>GB/AAAA-00000</inv:ucr>
        <inv:ucrType>D</inv:ucrType>
    </inv:queryUCR>
</inv:inventoryLinkingQueryRequest>'
```
