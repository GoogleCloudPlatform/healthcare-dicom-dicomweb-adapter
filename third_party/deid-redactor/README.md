# DICOM Redactor Library

The DICOM Redactor Library is a Java library designed to redact sensitive data contained in DICOM tags. This is an offline library which does not communicate with Google Cloud.

## Getting Started

### Building

The DICOM redactor library can be built using [Gradle](https://gradle.org/). Please refer to these [instructions](https://gradle.org/install/) to build Gradle for your system.

To build the library and examples:

```shell
cd redactor
./gradlew build
```

### Running unit tests

```shell
cd redactor
./gradlew test
```

## Configuration

The library's redaction is primarily configured using [protobuf](https://developers.google.com/protocol-buffers/). The configuration is similar to the [DicomConfig](https://cloud.google.com/healthcare/docs/reference/rpc/google.cloud.healthcare.v1beta1/deidentify#dicomconfig) for the deidentify operation in Google's Cloud Healthcare API (although the predefined filter profiles differ).

The user can configure which tags to redact/remove in one of 3 ways:

1. keep_list - a list of DICOM tags to keep untouched. Other tags are removed.
2. remove_list - a list of DICOM tags to remove. Other tags are kept untouched.
3. filter_profile - a predefined profile that will keep and remove particular tags.

See the full configuration [proto](lib/src/main/proto/DicomConfig.proto) for more info.

To view the sepcific tags removed for a certain profile, see the relevant [textproto](lib/src/main/resource/chc_basic.textproto).

## UID Regeneration

Regardless of the configuration, several UIDs will always be regenerated: SOPInstanceUID, StudyInstanceUID, SeriesInstanceUID, and MediaStorageSOPInstanceUID.
By default, these will be regenerated using the [UUID Derived UID](http://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_B.2.html) method. Optionally, when constucting a redactor, the user can specify their own prefix to use for the regenerated UIDs.

## Sample script

A [command line utility](examples) for redacting tags using the library has been included. To run:

```shell
cd redactor
./gradlew examples:tag_remover:run --args='-i in.dcm -o out.dcm -t PatientName'
```
