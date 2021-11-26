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

        @media only screen and (min-width: 1440px) {
            margin:0px auto;
            width: 1440px;
        }
    }

    .cell {
        flex: 0 1 auto;
        width: 25vw;
        height: auto;
        aspect-ratio: 1 / 1;

        @media only screen and (min-width: 768px) {
          width: 20vw;
        }

        @media only screen and (min-width: 1042px) {
          width: 12.5vw;
        }

        @media only screen and (min-width: 1440px) {
          width: 12.5%;
        }

        img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }
    }

</style>
