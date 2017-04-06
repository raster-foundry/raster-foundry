from .base import BaseModel
from rf.utils.io import get_session

class Upload(BaseModel):
    URL_PATH = '/api/uploads/'

    def __init__(self, organizationId, uploadStatus, fileType, uploadType, files,
                 datasource, metadata, visibility, id=None, createdAt=None,
                 createdBy=None, modifiedAt=None, modifiedBy=None):
        self.id = id
        self.createdAt = createdAt
        self.createdBy = createdBy
        self.modifiedAt = modifiedAt
        self.modifiedBy = modifiedBy
        self.organizationId = organizationId
        self.uploadStatus = uploadStatus
        self.fileType = fileType
        self.uploadType = uploadType
        self.files = files
        self.datasource = datasource
        self.metadata = metadata
        self.visibility = visibility

    def to_dict(self):
        return dict(
            id=self.id,
            createdAt=self.createdAt,
            createdBy=self.createdBy,
            modifiedAt=self.modifiedAt,
            modifiedBy=self.modifiedBy,
            organizationId=self.organizationId,
            uploadStatus=self.uploadStatus,
            fileType=self.fileType,
            uploadType=self.uploadType,
            files=self.files,
            datasource=self.datasource,
            metadata=self.metadata,
            visibility=self.visibility
        )

    def update_upload_status(self, status):
        self.uploadStatus = status
        return self.update()

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('organizationId'),
            d.get('uploadStatus'),
            d.get('fileType'),
            d.get('uploadType'),
            d.get('files'),
            d.get('datasource'),
            d.get('metadata'),
            d.get('visibility'),
            d.get('id'),
            d.get('createdAt'),
            d.get('createdBy'),
            d.get('modifiedAt'),
            d.get('modifiedBy')
        )

    @classmethod
    def get_importable_uploads(cls):
        url = '{HOST}{URL_PATH}'.format(HOST=cls.HOST, URL_PATH=cls.URL_PATH)
        session = get_session()
        response = session.get(url, params={'uploadStatus': 'uploaded'})
        response.raise_for_status()

        parsed = response.json()
        ids = [rec['id'] for rec in parsed['results']]
        page = 0
        while parsed['hasNext']:
            page += 1
            parsed = session.get(url, params={'uploadStatus': 'uploaded', 'page': page}).json()
            ids += [rec['id'] for rec in parsed['results']]
        return ids
