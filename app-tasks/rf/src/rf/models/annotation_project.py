from .base import BaseModel


class AnnotationProject(BaseModel):
    URL_PATH = "/api/annotation-projects/"

    def __init__(self, id, taskSizePixels):
        self.id = id
        self.taskSizePixels = taskSizePixels

    def to_dict(self):
        return dict(
            id=self.id,
            taskSizePixels=self.taskSizePixels
        )

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get("id"),
            d.get("taskSizePixels")
        )
