# prep work: go to magic domain http://mitm.it/ and export cert
# in windows, import as CA trust
# but for firefox: you need to import in firefox (certs) within firefox itself!

# to intercept calls: in main screen press intercept "i"  then filter for query q with  "~q /mtl/api"
# call gets stopped. enter to go to it, and edit with "e"

# start it with:
# set mtl_cookie=[value of mtlwebsession]
# mitmproxy -s C:\Users\patri\IdeaProjects\mytraillog\mtl-client\doc\mitmproxy\reverse.py

# set vite to use server backend: localhost:5172
# open firefox and use http://localhost:5173/mtl/


from mitmproxy import http, ctx
import logging
import os


# not working logging.basicConfig(filename='_mitmproxy.log', level=logging.DEBUG)

try:
    mtl_cookie = os.environ["mtl_cookie"]
    if not mtl_cookie:
        raise ValueError("Environment variable 'mtl_cookie' is not set.")
except:
    # logging.error("Environment variable 'mtl_cookie' not set. Exiting.")
    raise ValueError("Environment variable 'mtl_cookie' is not set.")

logging.info(f"Start with mtl_cookie={mtl_cookie}")

def request(flow: http.HTTPFlow) -> None:
    # Get the requested URL
    url = flow.request.pretty_url

    logging.debug(f"url: {url}")

    # Modify the destination based on the requested URL
    if url.startswith("http://localhost:5173/mtl/api"):
        flow.request.host = "mindalyze.com"
        flow.request.port = 443  # assuming HTTPS is used
        flow.request.scheme = "https"
        flow.request.cookies["mtlwebsession"] = mtl_cookie
        # flow.request.path = "/mtl/api" + flow.request.path.split('/mtl/api', 1)[1]
    elif url.startswith("http://localhost:5173/mtl/"):
        flow.request.host = "localhost"
        flow.request.port = 5173
        flow.request.scheme = "http"
        # flow.request.path = "/mtl/" + flow.request.path.split('/mtl/', 1)[1]

