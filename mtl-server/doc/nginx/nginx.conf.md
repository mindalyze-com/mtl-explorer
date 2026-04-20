This is the nginx proxy conf

There's the need to remove the "origin" header, otherwise chrome will crash.
````
    location /mtl {
        # rewrite ^/mtl/(.+)$ /$1 break;
        proxy_pass https://mindalyze.hopto.org:49040/mtl;
        proxy_ssl_verify off;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header Host $host;
        proxy_set_header Origin ""; # Remove the Origin header
        proxy_read_timeout     300;
        proxy_connect_timeout  300;
        # proxy_http_version 1.1;
        # proxy_set_header Connection "";
    }
````