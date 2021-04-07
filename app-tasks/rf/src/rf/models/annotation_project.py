from .base import BaseModel


class AnnotationProject(BaseModel):
    URL_PATH = "/api/annotation-projects/"

    def __init__(
        self,
        id,
        name,
        projectType,
        taskSizePixels,
        status,
        isActive,
        createdAt=None,
        createdBy=None,
        taskSizeMeters=None,
        aoi=None,
        labelersTeamId=None,
        validatorsTeamId=None,
        projectId=None,
        campaignId=None
    ):
        self.id = id
        self.name = name
        self.projectType = projectType
        self.taskSizePixels = taskSizePixels
        self.status = status
        self.isActive = isActive
        self.createdAt = createdAt
        self.createdBy = createdBy
        self.taskSizeMeters = taskSizeMeters
        self.aoi = aoi
        self.labelersTeamId = labelersTeamId
        self.validatorsTeamId = validatorsTeamId
        self.projectId = projectId
        self.campaignId = campaignId

    def to_dict(self):
        return dict(
            id=self.id,
            createdAt=self.createdAt,
            createdBy=self.createdBy,
            name=self.name,
            projectType=self.projectType,
            taskSizeMeters=self.taskSizeMeters,
            taskSizePixels=self.taskSizePixels,
            aoi=self.aoi,
            labelersTeamId=self.labelersTeamId,
            validatorsTeamId=self.validatorsTeamId,
            projectId=self.projectId,
            status=self.status,
            campaignId=self.campaignId,
            isActive=self.isActive
        )

    def update_status(self, status):
        self.status = status
        return self.update()

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get("id"),
            d.get("name"),
            d.get("projectType"),
            d.get("taskSizePixels"),
            d.get("status"),
            d.get("isActive"),
            createdAt=d.get("createdAt"),
            createdBy=d.get("createdBy"),
            taskSizeMeters=d.get("taskSizeMeters"),
            aoi=d.get("aoi"),
            labelersTeamId=d.get("labelersTeamId"),
            validatorsTeamId=d.get("validatorsTeamId"),
            projectId=d.get("projectId"),
            campaignId=d.get("campaignId")
        )
