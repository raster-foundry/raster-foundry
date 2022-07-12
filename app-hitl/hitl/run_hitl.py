import click

@click.command()
@click.argument('hitl_project_id')
def run_hitl(hitl_project_id):
    print(f'processing project: {hitl_project_id}')

if __name__ == '__main__':
    run_hitl()