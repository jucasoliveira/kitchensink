The original software spread the Data accross six separate tables, which made it difficult to query and maintain. The new design consolidates the data into a single table, which simplifies the schema and improves performance. This decision was made to enhance the overall efficiency of the application and to reduce complexity in data management.


```
Customer
├── userId: "jsmith"
├── profile: { language: "en", favoriteCategory: "dogs" }
└── account:
    ├── status: "active"
    ├── creditCard: { number, type, expiry }
    └── contactInfo:
        ├── name, email, phone
        └── address: { street, city, state, zip, country }
```