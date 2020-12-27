<template>
    <div>
        <p>loading..</p>
    </div>
</template>

<script lang="ts">
import { Vue } from 'vue-class-component'
import { authService } from '../auth/auth-service'
import router from '../router'

export default class Callback extends Vue {
  public created () {
    console.log('callback created')
    authService.handleAuthentication().then(() => router.push('/'))
  }
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
