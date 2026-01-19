db = db.getSiblingDB('demos-db');

db.createUser({
  user: "wd",
  pwd: "wd",
  roles: ["readWrite"]
});

db.createUser({
  user: "me",
  pwd: "me",
  roles: [
    {
      role: "readWrite",
      db: "demos-db"
    },
    {
      role: "readAnyDatabase",
      db: "admin"
    }
  ]
});

db.createCollection('patients')
db.createCollection('locations')
db.createCollection('configurations')
db.createCollection('configurationSchemas')
db.createCollection('products')
db.createCollection('flink_source_works')
