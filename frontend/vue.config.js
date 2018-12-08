module.exports = {
    pages: {
        index: {
            // entry for the page
            entry: __dirname + '/src/main/resources/vue/src/main.ts'
        }
    },
    // Change build paths to make them Maven compatible
    // see https://cli.vuejs.org/config/
    outputDir: __dirname + '/target/dist',
    assetsDir: 'static'

}