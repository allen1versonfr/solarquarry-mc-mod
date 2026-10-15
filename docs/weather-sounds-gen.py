import numpy as np, wave, subprocess, os
SR=44100
rng=np.random.default_rng(20261015)
def n(d): return int(SR*d)
def t(d): return np.arange(n(d))/SR
def env(N,a=0.02,r=0.1):
    e=np.ones(N); A=int(SR*a); R=int(SR*r)
    if A: e[:A]=np.linspace(0,1,A)
    if R: e[-R:]=np.minimum(e[-R:],np.linspace(1,0,R))
    return e
def hum(d,f,harm=(1,0.5,0.25),wob=0.3):
    x=t(d); s=sum(a*np.sin(2*np.pi*f*(i+1)*x) for i,a in enumerate(harm))
    return s*(1+0.15*np.sin(2*np.pi*wob*x))
def sweep(d,f0,f1,harm=(1,0.4)):
    x=t(d); f=np.linspace(f0,f1,len(x)); ph=2*np.pi*np.cumsum(f)/SR
    return sum(a*np.sin(ph*(i+1)) for i,a in enumerate(harm))
def noise(N): return rng.uniform(-1,1,N)
def lp(x,k=8): return np.convolve(x,np.ones(k)/k,mode='same')
def crackle(d,density=60):
    x=np.zeros(n(d))
    for _ in range(int(density*d)):
        L=int(rng.integers(100,800)); p=int(rng.integers(0,len(x)-L))
        x[p:p+L]+=noise(L)*np.exp(-np.linspace(0,6,L))
    return x
def dec(N,k): return np.exp(-np.linspace(0,k,N))
def save(name,x,loop=False,vol=0.8):
    x=x/(np.max(np.abs(x))+1e-9)*vol
    if loop:
        F=int(SR*0.05); x[:F]*=np.linspace(0,1,F)**0.5; x[-F:]*=np.linspace(1,0,F)**0.5
    w=f'{name}.wav'
    with wave.open(w,'wb') as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR); f.writeframes((x*32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',w,'-c:a','libvorbis','-q:a','4',f'{name}.ogg'],check=True); os.remove(w)
save('idle_hum', hum(4,55,(1,0.3,0.1),0.25)+0.05*lp(noise(n(4)),40), loop=True, vol=0.5)
save('capacitor_hum', hum(3,120,(1,0.6,0.3,0.15),2.0)+0.08*lp(noise(n(3)),20), loop=True, vol=0.55)
x=sweep(2,60,420,(1,0.5,0.25)); save('power_intake', x*np.linspace(0.2,1,len(x))*env(len(x),0.05,0.2))
x=sweep(2.5,100,900,(1,0.5,0.3))+0.3*crackle(2.5,30); save('core_charge', x*np.linspace(0.3,1,len(x))*env(len(x),0.05,0.3))
x=hum(2,80,(1,0.5),4.0)*(1+0.5*np.sign(np.sin(2*np.pi*3*t(2))))+0.2*lp(noise(n(2)),10); save('ring_rotation', x, loop=True, vol=0.5)
a=sweep(0.25,300,180,(1,0.6))*env(n(0.25),0.005,0.05); b=noise(n(0.08))*dec(n(0.08),8)*1.5; c=lp(noise(n(0.12)),4)*dec(n(0.12),10)
save('servo_lock', np.concatenate([a,np.zeros(n(0.05)),b,c]))
save('arc', crackle(0.8,120)+0.2*np.sin(2*np.pi*2000*t(0.8))*dec(n(0.8),4))
x=lp(noise(n(1.2)),3)*dec(n(1.2),5)+sweep(1.2,800,60,(1,0.5))*dec(n(1.2),3); save('activation_burst', x, vol=0.9)
x=sweep(2,200,600,(1,0.7,0.4))*env(n(2),0.02,0.6)+0.3*crackle(2,40); save('field_activate', x)
x=lp(noise(n(3)),2)*dec(n(3),2.5)+0.6*sweep(3,40,30,(1,0.8))*env(n(3),0.01,1.0)+0.5*crackle(3,200); save('storm_activate', x, vol=0.95)
x=t(2.5); s=sum(np.sin(2*np.pi*f*x)*np.exp(-2*np.clip(x-i*0.3,0,None))*(x>=i*0.3) for i,f in enumerate([523.25,659.25,783.99,1046.5,783.99,659.25]))
save('time_activate', s*env(len(x),0.01,0.4)+0.15*hum(2.5,110,(1,0.5),1))
save('operating_hum', hum(4,70,(1,0.7,0.4,0.2),0.6)+0.1*lp(noise(n(4)),15)+0.05*crackle(4,15), loop=True, vol=0.55)
x=t(1.6); save('low_power', np.sin(2*np.pi*880*x)*((x%0.4)<0.18)*env(len(x),0.01,0.05), vol=0.6)
x=sweep(2.5,400,40,(1,0.5,0.25)); save('shutdown', x*np.linspace(1,0,len(x))**1.5)
x=t(1.2); s=np.sin(2*np.pi*659.25*x)*(x<0.3)+np.sin(2*np.pi*987.77*x)*(x>=0.25); save('complete', s*np.exp(-2.5*x)*env(len(x),0.005,0.2), vol=0.6)
x=t(0.7); s=np.sign(np.sin(2*np.pi*140*x))*0.6+np.sin(2*np.pi*95*x); save('error', s*((x%0.3)<0.22)*env(len(x),0.005,0.05), vol=0.6)
print(sorted(f for f in os.listdir('.') if f.endswith('.ogg')))
