import substitution
from general import *

# clear data
clear_data()

# install environment
verify_result(install_environment())

# build adapter image
verify_result(build_adapter_image(substitution.IMAGEPROJECT))

# publish
verify_result(publish_image(substitution.IMAGEPROJECT, substitution.PUBLISH,
                            "", substitution.ACCESS_TOKEN_BASE64,
                            substitution.PROJECT, substitution.KMS_LOCATION, substitution.KMS_KEYRING,
                            substitution.KMS_KEY))