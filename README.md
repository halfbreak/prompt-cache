How to test:
```curl
curl --location 'http://localhost:8080/cache' \
--header 'Content-Type: application/json' \
--data '{
    "prompt": "hello",
    "response": "Hi I am here to help you"
}'
```

Then we test exact match:
```curl
curl --location 'http://localhost:8080/cache?prompt=hello'
```

Then we test similarity match:
```curl
curl --location 'http://localhost:8080/cache?prompt=hello%20sir'
```
