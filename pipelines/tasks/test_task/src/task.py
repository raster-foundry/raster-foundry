import luigi
from luigi import s3


class PrerequisiteTask(luigi.Task):
    """Generate prerequisites for the main task"""
    # no requirements
    def run(self):
        with self.output().open('w') as out:
            out.write('Success!')

    def output(self):
        raise Exception('Put in a valid S3 path before running me!')
        #return s3.S3Target('s3://<BUCKET_NAME_HERE>/file.txt')


class TestTask(luigi.Task):
    name = luigi.Parameter()

    def requires(self):
        return PrerequisiteTask()

    def run(self):
        print('Hello, {}'.format(self.name))


if __name__ == '__main__':
    luigi.run(main_task_cls=TestTask)
