export interface ICache {
    get(key: string): Promise<any>;
    set(key: string, value: any): Promise<any>;
}
