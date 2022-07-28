from .base import BaseModel


class HITLJob(BaseModel):
    URL_PATH = "/api/hitl-jobs/"

    def __init__(
        self,
        id=None,
        createdAt=None,
        createdBy=None,
        modifiedAt=None,
        owner=None,
        campaignId=None,
        projectId=None,
        status=None,
        version=None
    ):
        self.id = id
        self.createdAt = createdAt
        self.createdBy = createdBy
        self.modifiedAt = modifiedAt
        self.owner = owner
        self.campaignId = campaignId
        self.projectId = projectId
        self.status = status
        self.version = version

    def to_dict(self):
        return dict(
            id=self.id,
            createdAt=self.createdAt,
            createdBy=self.createdBy,
            modifiedAt=self.modifiedAt,
            owner=self.owner,
            campaignId=self.campaignId,
            projectId=self.projectId,
            status=self.status,
            version=self.version
        )

    def update_job_status(self, status):
        self.status = status
        return self.update()

    @classmethod
    def from_dict(cls, d):
        return cls(
            id=d.get("id"),
            createdAt=d.get("createdAt"),
            createdBy=d.get("createdBy"),
            modifiedAt=d.get("modifiedAt"),
            owner=d.get("owner"),
            campaignId=d.get("campaignId"),
            projectId=d.get("projectId"),
            status=d.get("status"),
            version=d.get("version"),
        )

