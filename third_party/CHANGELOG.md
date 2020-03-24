jai-imageio-jpeg2000
--------------------

1) Fix for use with dcm4che: ignore up to 3 trailing bytes, anything less than 4 can't comprise a valid box. [src/main/java/jj2000/j2k/fileformat/reader/FileFormatReader.java changes](https://github.com/red1408/jai-imageio-jpeg2000/commit/6937156851a54f12f0ea3c7ea673ecdaf57f2f54?diff=unified)

2) Added gradle build file and commented out System.runFinalizersOnExit() as unsupported by java above version 8 [full commit](https://github.com/GoogleCloudPlatform/healthcare-dicom-dicomweb-adapter/pull/61/commits/9fe84b01a3f831260372ef36caed31875111017a)