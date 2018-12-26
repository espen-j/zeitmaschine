<template>
    <div id="app">
        <Gallery/>
    </div>
</template>

<script lang="ts">
    import {Component, Vue} from 'vue-property-decorator';
    import Gallery from './components/Gallery.vue';

    @Component({
        components: {
            Gallery
        }
    })
    export default class App extends Vue {
    }
</script>

<style>
    html, body {
        margin: 0;
        padding: 0;
    }
    #app {
    }
</style>
