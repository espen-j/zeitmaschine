module.exports = {
    pages: {
        index: {
            // entry for the page
            entry: __dirname + '/src/main/resources/vue/src/main.ts',
            // the source template
            template: __dirname + '/src/main/resources/vue/public/index.html',
            // when using title option,
            // template title tag needs to be <title><%= htmlWebpackPlugin.options.title %></title>
            title: 'zeitmaschine'
        }
    },
    // Change build paths to make them Maven compatible
    // see https://cli.vuejs.org/config/
    outputDir: __dirname + '/target/dist'
}
