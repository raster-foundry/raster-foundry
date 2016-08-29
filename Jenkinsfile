node {
	stage 'checkout'

	checkout scm

	stage 'cibuild'
	sh "scripts/cibuild"
	
	stage 'cipublish'
	sh "sleep 3;"
}