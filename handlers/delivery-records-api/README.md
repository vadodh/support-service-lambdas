Delivery Records Api
====================

This api provides access to delivery records for a subscription. Delivery records are stored in Salesforce. 

Usage
=====

All endpoints require...

- `x-api-key` header (generated by the `DeliveryRecordsApiKey` in the CloudFormation)
- and either...
  - `x-identity-id` header which specifies the identityID of the user to request data for _(sent in the `manage-frontend` use-case)_
  - `x-salesforce-contact-id` header which specifies the Salesforce contact ID of the user to request data for _(sent in the CSR UI (in Salesforce) use-case)_

| Method | Endpoint | Description |
| --- | --- | --- | 
| GET | `/{STAGE}/delivery-records/{SUBSCRIPTION_NAME}?startDate={yyyy-MM-dd}&endDate={yyyy-MM-dd}` | Returns the delivery records for a subscription optionally filtered based on delivery date |