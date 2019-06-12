<template>
    <div>
        <p>loading..</p>
    </div>
</template>

<script lang="ts">
    import {Component, Vue} from 'vue-property-decorator';

    @Component
    export default class Callback extends Vue {

    }
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style lang="scss">
    .container {
        display: flex;
        flex-wrap: wrap;
    }

    .cell {
        width: 25vw;
        height: 25vw;
        flex: 0 1 auto;

        img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }
    }

</style>
