package com.google.cloud.healthcare.imaging.dicomadapter.backupuploader;

import com.google.api.gax.paging.Page;
import com.google.cloud.Policy;
import com.google.cloud.ReadChannel;
import com.google.cloud.RestorableState;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.HmacKey;
import com.google.cloud.storage.PostPolicyV4;
import com.google.cloud.storage.ServiceAccount;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageBatch;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.StorageOptions;
import org.mockito.Mockito;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.when;

public class FakeStorage implements Storage {

    private HashMap<String, byte[]> fakeObjects;

    public FakeStorage() {
        this.fakeObjects = new HashMap<>();
    }

    public FakeStorage(String uploadObject, byte[] data) {
        this();
        fakeObjects.put(uploadObject, data);
    }

    @Override
    public Bucket create(BucketInfo bucketInfo, BucketTargetOption... options) {
        return null;
    }

    @Override
    public Blob create(BlobInfo blobInfo, BlobTargetOption... options) {
        return null;
    }

    @Override
    public Blob create(BlobInfo blobInfo, byte[] content, BlobTargetOption... options) {
        return null;
    }

    @Override
    public Blob create(BlobInfo blobInfo, byte[] content, int offset, int length, BlobTargetOption... options) {
        return null;
    }

    @Override
    public Blob create(BlobInfo blobInfo, InputStream content, BlobWriteOption... options) {
        return null;
    }

    @Override
    public Blob createFrom(BlobInfo blobInfo, Path path, BlobWriteOption... options) throws IOException {
        return null;
    }

    @Override
    public Blob createFrom(BlobInfo blobInfo, Path path, int bufferSize, BlobWriteOption... options) throws IOException {
        return null;
    }

    @Override
    public Blob createFrom(BlobInfo blobInfo, InputStream content, BlobWriteOption... options) throws IOException {
        fakeObjects.put(blobInfo.getName(), content.readAllBytes());
        return null;
    }

    @Override
    public Blob createFrom(BlobInfo blobInfo, InputStream content, int bufferSize, BlobWriteOption... options) throws IOException {
        return null;
    }

    @Override
    public Bucket get(String bucket, BucketGetOption... options) {
        return null;
    }

    @Override
    public Bucket lockRetentionPolicy(BucketInfo bucket, BucketTargetOption... options) {
        return null;
    }

    @Override
    public Blob get(String bucket, String blob, BlobGetOption... options) {
        return null;
    }

    @Override
    public Blob get(BlobId blob, BlobGetOption... options) {
        return null;
    }

    @Override
    public Blob get(BlobId blobId) {
       // Can`t create instance of Blob, so used mock for it
       Blob blob = Mockito.mock(Blob.class);
       when(blob.reader()).thenReturn(this.reader(blobId));
       return blob;
    }

    @Override
    public Page<Bucket> list(BucketListOption... options) {
        return null;
    }

    @Override
    public Page<Blob> list(String bucket, BlobListOption... options) {
        return null;
    }

    @Override
    public Bucket update(BucketInfo bucketInfo, BucketTargetOption... options) {
        return null;
    }

    @Override
    public Blob update(BlobInfo blobInfo, BlobTargetOption... options) {
        return null;
    }

    @Override
    public Blob update(BlobInfo blobInfo) {
        return null;
    }

    @Override
    public boolean delete(String bucket, BucketSourceOption... options) {
        return false;
    }

    @Override
    public boolean delete(String bucket, String blob, BlobSourceOption... options) {
        try {
            fakeObjects.remove(Paths.get(bucket, blob).toString());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public boolean delete(BlobId blob, BlobSourceOption... options) {
        return false;
    }

    @Override
    public boolean delete(BlobId blob) {
        return false;
    }

    @Override
    public Blob compose(ComposeRequest composeRequest) {
        return null;
    }

    @Override
    public CopyWriter copy(CopyRequest copyRequest) {
        return null;
    }

    @Override
    public byte[] readAllBytes(String bucket, String blob, BlobSourceOption... options) {
        return new byte[0];
    }

    @Override
    public byte[] readAllBytes(BlobId blob, BlobSourceOption... options) {
        return new byte[0];
    }

    @Override
    public StorageBatch batch() {
        return null;
    }

    @Override
    public ReadChannel reader(String bucket, String blob, BlobSourceOption... options) {
        return null;
    }

    @Override
    public ReadChannel reader(BlobId blob, BlobSourceOption... options) {
        FakeChannel fakeChannel = new FakeChannel(fakeObjects.get(blob.getName()));
        return fakeChannel;
    }

    @Override
    public WriteChannel writer(BlobInfo blobInfo, BlobWriteOption... options) {
        return null;
    }

    @Override
    public WriteChannel writer(URL signedURL) {
        return null;
    }

    @Override
    public URL signUrl(BlobInfo blobInfo, long duration, TimeUnit unit, SignUrlOption... options) {
        return null;
    }

    @Override
    public PostPolicyV4 generateSignedPostPolicyV4(BlobInfo blobInfo, long duration, TimeUnit unit, PostPolicyV4.PostFieldsV4 fields, PostPolicyV4.PostConditionsV4 conditions, PostPolicyV4Option... options) {
        return null;
    }

    @Override
    public PostPolicyV4 generateSignedPostPolicyV4(BlobInfo blobInfo, long duration, TimeUnit unit, PostPolicyV4.PostFieldsV4 fields, PostPolicyV4Option... options) {
        return null;
    }

    @Override
    public PostPolicyV4 generateSignedPostPolicyV4(BlobInfo blobInfo, long duration, TimeUnit unit, PostPolicyV4.PostConditionsV4 conditions, PostPolicyV4Option... options) {
        return null;
    }

    @Override
    public PostPolicyV4 generateSignedPostPolicyV4(BlobInfo blobInfo, long duration, TimeUnit unit, PostPolicyV4Option... options) {
        return null;
    }

    @Override
    public List<Blob> get(BlobId... blobIds) {
        return null;
    }

    @Override
    public List<Blob> get(Iterable<BlobId> blobIds) {
        return null;
    }

    @Override
    public List<Blob> update(BlobInfo... blobInfos) {
        return null;
    }

    @Override
    public List<Blob> update(Iterable<BlobInfo> blobInfos) {
        return null;
    }

    @Override
    public List<Boolean> delete(BlobId... blobIds) {
        return null;
    }

    @Override
    public List<Boolean> delete(Iterable<BlobId> blobIds) {
        return null;
    }

    @Override
    public Acl getAcl(String bucket, Acl.Entity entity, BucketSourceOption... options) {
        return null;
    }

    @Override
    public Acl getAcl(String bucket, Acl.Entity entity) {
        return null;
    }

    @Override
    public boolean deleteAcl(String bucket, Acl.Entity entity, BucketSourceOption... options) {
        return false;
    }

    @Override
    public boolean deleteAcl(String bucket, Acl.Entity entity) {
        return false;
    }

    @Override
    public Acl createAcl(String bucket, Acl acl, BucketSourceOption... options) {
        return null;
    }

    @Override
    public Acl createAcl(String bucket, Acl acl) {
        return null;
    }

    @Override
    public Acl updateAcl(String bucket, Acl acl, BucketSourceOption... options) {
        return null;
    }

    @Override
    public Acl updateAcl(String bucket, Acl acl) {
        return null;
    }

    @Override
    public List<Acl> listAcls(String bucket, BucketSourceOption... options) {
        return null;
    }

    @Override
    public List<Acl> listAcls(String bucket) {
        return null;
    }

    @Override
    public Acl getDefaultAcl(String bucket, Acl.Entity entity) {
        return null;
    }

    @Override
    public boolean deleteDefaultAcl(String bucket, Acl.Entity entity) {
        return false;
    }

    @Override
    public Acl createDefaultAcl(String bucket, Acl acl) {
        return null;
    }

    @Override
    public Acl updateDefaultAcl(String bucket, Acl acl) {
        return null;
    }

    @Override
    public List<Acl> listDefaultAcls(String bucket) {
        return null;
    }

    @Override
    public Acl getAcl(BlobId blob, Acl.Entity entity) {
        return null;
    }

    @Override
    public boolean deleteAcl(BlobId blob, Acl.Entity entity) {
        return false;
    }

    @Override
    public Acl createAcl(BlobId blob, Acl acl) {
        return null;
    }

    @Override
    public Acl updateAcl(BlobId blob, Acl acl) {
        return null;
    }

    @Override
    public List<Acl> listAcls(BlobId blob) {
        return null;
    }

    @Override
    public HmacKey createHmacKey(ServiceAccount serviceAccount, CreateHmacKeyOption... options) {
        return null;
    }

    @Override
    public Page<HmacKey.HmacKeyMetadata> listHmacKeys(ListHmacKeysOption... options) {
        return null;
    }

    @Override
    public HmacKey.HmacKeyMetadata getHmacKey(String accessId, GetHmacKeyOption... options) {
        return null;
    }

    @Override
    public void deleteHmacKey(HmacKey.HmacKeyMetadata hmacKeyMetadata, DeleteHmacKeyOption... options) {

    }

    @Override
    public HmacKey.HmacKeyMetadata updateHmacKeyState(HmacKey.HmacKeyMetadata hmacKeyMetadata, HmacKey.HmacKeyState state, UpdateHmacKeyOption... options) {
        return null;
    }

    @Override
    public Policy getIamPolicy(String bucket, BucketSourceOption... options) {
        return null;
    }

    @Override
    public Policy setIamPolicy(String bucket, Policy policy, BucketSourceOption... options) {
        return null;
    }

    @Override
    public List<Boolean> testIamPermissions(String bucket, List<String> permissions, BucketSourceOption... options) {
        return null;
    }

    @Override
    public ServiceAccount getServiceAccount(String projectId) {
        return null;
    }

    @Override
    public StorageOptions getOptions() {
        return null;
    }

    class FakeChannel implements ReadChannel {

        ReadableByteChannel readableByteChannel;

        public FakeChannel(byte[] buffer) {
            this.readableByteChannel = Channels.newChannel(new ByteArrayInputStream(buffer));
        }

        @Override
        public boolean isOpen() {
            return readableByteChannel.isOpen();
        }

        @Override
        public void close() {
            try {
                readableByteChannel.close();
            } catch (IOException e) { }
        }

        @Override
        public void seek(long position) throws IOException {

        }

        @Override
        public void setChunkSize(int chunkSize) {

        }

        @Override
        public RestorableState<ReadChannel> capture() {
            return null;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return readableByteChannel.read(dst);
        }
    }
}
